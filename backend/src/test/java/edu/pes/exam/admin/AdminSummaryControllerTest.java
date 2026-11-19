package edu.pes.exam.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminSummaryControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdminController controller = new AdminController(jdbc, mock(PasswordEncoder.class));

    @Test
    void summaryCountsPersistedUsersExamsAndAttempts() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE is_active=TRUE", Long.class)).thenReturn(12L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM exams", Long.class)).thenReturn(7L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM attempts", Long.class)).thenReturn(25L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM attempts WHERE status IN ('SUBMITTED','TIMED_OUT')", Long.class)).thenReturn(18L);

        Map<String, Long> summary = controller.summary();

        assertEquals(Map.of("users", 12L, "exams", 7L, "attempts", 25L, "submittedAttempts", 18L), summary);
        verify(jdbc).queryForObject("SELECT COUNT(*) FROM users WHERE is_active=TRUE", Long.class);
        verify(jdbc).queryForObject("SELECT COUNT(*) FROM exams", Long.class);
        verify(jdbc).queryForObject("SELECT COUNT(*) FROM attempts", Long.class);
    }

    @Test
    void emptyDatabaseReturnsZeroCounts() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE is_active=TRUE", Long.class)).thenReturn(0L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM exams", Long.class)).thenReturn(0L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM attempts", Long.class)).thenReturn(0L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM attempts WHERE status IN ('SUBMITTED','TIMED_OUT')", Long.class)).thenReturn(0L);

        assertEquals(Map.of("users", 0L, "exams", 0L, "attempts", 0L, "submittedAttempts", 0L), controller.summary());
    }

    @Test
    void controllerRequiresAdminRole() {
        PreAuthorize authorization = AdminController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertEquals("hasRole('ADMIN')", authorization.value());
    }
}
