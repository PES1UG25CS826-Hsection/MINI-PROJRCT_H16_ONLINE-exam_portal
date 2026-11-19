package edu.pes.exam.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

class ExamReportCsvTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ExamController controller = new ExamController(jdbc, new ObjectMapper(), new ExamScoringService());
    private final UUID teacherId = UUID.randomUUID();
    private final UUID examId = UUID.randomUUID();
    private final PortalPrincipal teacher = new PortalPrincipal(teacherId, "teacher@example.test", "TEACHER");

    @Test
    void csvEscapesNamesAndEmailFieldsAndUsesExamScopedRows() throws Exception {
        allowOwnedExam();
        when(jdbc.query(startsWith("SELECT u.full_name,u.email"), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), eq(examId)))
                .thenAnswer(invocation -> {
                    ResultSet row = mock(ResultSet.class);
                    when(row.getString("full_name")).thenReturn("Sam \"Ace\", Jr");
                    when(row.getString("email")).thenReturn("sam,ace@example.test");
                    when(row.getBigDecimal("score")).thenReturn(new BigDecimal("8.00"));
                    when(row.getBigDecimal("max_score")).thenReturn(new BigDecimal("10.00"));
                    when(row.getBigDecimal("percentage")).thenReturn(new BigDecimal("80.00"));
                    when(row.getBoolean("passed")).thenReturn(true);
                    when(row.getTimestamp("graded_at")).thenReturn(Timestamp.from(Instant.parse("2026-01-02T03:04:05Z")));
                    @SuppressWarnings("unchecked") RowMapper<java.util.Map<String, Object>> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });

        var response = controller.reportCsv(examId, teacher);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"Sam \"\"Ace\"\", Jr\",\"sam,ace@example.test\",8.00,10.00,80.00,true"));
        verify(jdbc).query(startsWith("SELECT u.full_name,u.email"), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), eq(examId));
    }

    @Test
    void rejectsCsvForExamOwnedByAnotherTeacher() throws Exception {
        stubExamRow();
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM exams WHERE id=? AND created_by=?"), eq(Integer.class), eq(examId), eq(teacherId)))
                .thenReturn(0);

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> controller.reportCsv(examId, teacher));

        assertEquals(403, error.getStatusCode().value());
    }

    private void allowOwnedExam() throws Exception {
        stubExamRow();
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM exams WHERE id=? AND created_by=?"), eq(Integer.class), eq(examId), eq(teacherId)))
                .thenReturn(1);
    }

    private void stubExamRow() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(examId);
        when(row.getString("title")).thenReturn("Midterm");
        when(row.getString("subject")).thenReturn("Math");
        when(row.getString("status")).thenReturn("PUBLISHED");
        when(row.getInt("duration_minutes")).thenReturn(60);
        when(row.getTimestamp("starts_at")).thenReturn(Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        when(row.getTimestamp("ends_at")).thenReturn(Timestamp.from(Instant.parse("2026-02-01T00:00:00Z")));
        when(row.getBigDecimal("total_marks")).thenReturn(new BigDecimal("10.00"));
        when(row.getBigDecimal("passing_marks")).thenReturn(new BigDecimal("5.00"));
        when(row.getInt("negative_mark_percent")).thenReturn(0);
        when(row.getInt("max_attempts")).thenReturn(1);
        when(row.getBoolean("show_answers_after_submit")).thenReturn(false);
        when(jdbc.query(startsWith("SELECT id,title,subject,status"), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), eq(examId)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked") RowMapper<Object> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });
    }
}
