package edu.pes.exam.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pes.exam.security.PortalPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

class ExamStudentEligibilityTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ExamController controller = new ExamController(jdbc, new ObjectMapper(), new ExamScoringService());
    private final UUID studentId = UUID.randomUUID();
    private final UUID examId = UUID.randomUUID();
    private final PortalPrincipal student = new PortalPrincipal(studentId, "student@example.test", "STUDENT");

    @Test
    void studentExamListQueryRequiresPublishedExamAndGroupMembership() {
        when(jdbc.query(startsWith("SELECT DISTINCT e.id"), any(RowMapper.class), eq(studentId)))
                .thenReturn(List.of());

        controller.exams(student);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(studentId));
        assertTrue(sql.getValue().contains("e.status='PUBLISHED' AND gm.student_id=?"));
        assertTrue(sql.getValue().contains("JOIN exam_groups eg ON eg.exam_id=e.id JOIN group_members gm ON gm.group_id=eg.group_id"));
    }

    @Test
    void rejectsDirectStartWhenStudentIsNotInAssignedGroup() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(examId);
        when(row.getString("title")).thenReturn("Midterm");
        when(row.getString("subject")).thenReturn("Math");
        when(row.getString("status")).thenReturn("PUBLISHED");
        when(row.getInt("duration_minutes")).thenReturn(60);
        when(row.getTimestamp("starts_at")).thenReturn(Timestamp.from(Instant.now().minusSeconds(60)));
        when(row.getTimestamp("ends_at")).thenReturn(Timestamp.from(Instant.now().plusSeconds(3600)));
        when(row.getBigDecimal("total_marks")).thenReturn(new BigDecimal("20.00"));
        when(row.getBigDecimal("passing_marks")).thenReturn(new BigDecimal("10.00"));
        when(row.getInt("negative_mark_percent")).thenReturn(0);
        when(row.getInt("max_attempts")).thenReturn(1);
        when(row.getBoolean("show_answers_after_submit")).thenReturn(false);
        when(jdbc.query(startsWith("SELECT id,title,subject,status"), any(RowMapper.class), eq(examId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM exam_groups"), eq(Integer.class), eq(examId), eq(studentId)))
                .thenReturn(0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.start(examId, student, mock(HttpServletRequest.class)));

        assertEquals(404, error.getStatusCode().value());
        verify(jdbc, org.mockito.Mockito.never()).update(startsWith("INSERT INTO attempts"),
                org.mockito.ArgumentMatchers.<Object[]>any());
    }
}
