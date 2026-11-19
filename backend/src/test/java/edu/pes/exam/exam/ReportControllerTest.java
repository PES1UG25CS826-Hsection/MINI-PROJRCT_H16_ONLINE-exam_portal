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
import java.util.Map;
import java.util.UUID;

import edu.pes.exam.security.PortalPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

class ReportControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ReportController controller = new ReportController(jdbc);
    private final UUID teacherId = UUID.randomUUID();
    private final UUID examId = UUID.randomUUID();
    private final PortalPrincipal teacher = new PortalPrincipal(teacherId, "teacher@example.test", "TEACHER");

    @Test
    void returnsPersistedMetricsForTeacherOwnedExam() throws Exception {
        allowOwnedExam();
        when(jdbc.queryForObject(startsWith("SELECT COUNT(r.id)"), any(RowMapper.class), eq(examId)))
                .thenAnswer(invocation -> {
                    ResultSet row = mock(ResultSet.class);
                    when(row.getLong("attempts")).thenReturn(4L);
                    when(row.getBigDecimal("average")).thenReturn(new BigDecimal("72.50"));
                    when(row.getBigDecimal("highest")).thenReturn(new BigDecimal("98.00"));
                    when(row.getBigDecimal("lowest")).thenReturn(new BigDecimal("41.00"));
                    when(row.getBigDecimal("pass_percentage")).thenReturn(new BigDecimal("75.00"));
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return mapper.mapRow(row, 0);
                });

        Map<String, Object> report = controller.examReport(examId, teacher);

        assertEquals(4L, report.get("attempts"));
        assertEquals(new BigDecimal("72.50"), report.get("average"));
        assertEquals(new BigDecimal("98.00"), report.get("highest"));
        assertEquals(new BigDecimal("41.00"), report.get("lowest"));
        assertEquals(new BigDecimal("75.00"), report.get("passPercentage"));
        verify(jdbc).queryForObject(startsWith("SELECT COUNT(r.id)"), any(RowMapper.class), eq(examId));
    }

    @Test
    void aggregateQueryUsesZeroDefaultsForExamWithoutResults() throws Exception {
        allowOwnedExam();
        when(jdbc.queryForObject(startsWith("SELECT COUNT(r.id)"), any(RowMapper.class), eq(examId)))
                .thenAnswer(invocation -> {
                    ResultSet row = mock(ResultSet.class);
                    when(row.getLong("attempts")).thenReturn(0L);
                    when(row.getBigDecimal("average")).thenReturn(BigDecimal.ZERO);
                    when(row.getBigDecimal("highest")).thenReturn(BigDecimal.ZERO);
                    when(row.getBigDecimal("lowest")).thenReturn(BigDecimal.ZERO);
                    when(row.getBigDecimal("pass_percentage")).thenReturn(BigDecimal.ZERO);
                    return ((RowMapper<?>) invocation.getArgument(1)).mapRow(row, 0);
                });

        Map<String, Object> report = controller.examReport(examId, teacher);

        assertEquals(0L, report.get("attempts"));
        assertEquals(BigDecimal.ZERO, report.get("average"));
        assertEquals(BigDecimal.ZERO, report.get("passPercentage"));
    }

    @Test
    void preventsTeacherFromReadingAnotherTeachersReport() {
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM exams"), eq(Integer.class), eq(examId), eq("TEACHER"), eq(teacherId)))
                .thenReturn(0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.examReport(examId, teacher));

        assertEquals(404, error.getStatusCode().value());
        assertTrue(error.getReason().contains("Exam not found"));
    }

    private void allowOwnedExam() {
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM exams"), eq(Integer.class), eq(examId), eq("TEACHER"), eq(teacherId)))
                .thenReturn(1);
    }
}
