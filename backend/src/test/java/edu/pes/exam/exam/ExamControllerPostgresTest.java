package edu.pes.exam.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pes.exam.security.PortalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.flywaydb.core.Flyway;

@Testcontainers(disabledWithoutDocker = true)
class ExamControllerPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("exam_create_test").withUsername("exam_test").withPassword("test-only-password");

    private JdbcTemplate jdbc;
    private ExamController controller;
    private UUID teacherId;
    private PortalPrincipal teacher;
    private final Instant start = Instant.parse("2026-12-01T09:00:00Z");

    @BeforeEach
    void migrateAndInsertTeacher() {
        DataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        teacherId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,full_name,role) VALUES (?,?,?,'Issue Seven Teacher','TEACHER')",
                teacherId, "teacher-" + teacherId + "@example.test", "test-hash");
        teacher = new PortalPrincipal(teacherId, "teacher@example.test", "TEACHER");
        controller = new ExamController(jdbc, new ObjectMapper(), new ExamScoringService());
    }

    @Test
    void migratedPostgresSchemaPersistsDraftAndAuditEvent() {
        var request = request("Algebra final", "Mathematics", 45, start, start.plusSeconds(7200),
                new BigDecimal("40"), new BigDecimal("24"));

        Map<String, Object> response = controller.createExam(request, teacher);

        assertEquals("DRAFT", response.get("status"));
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM exams WHERE id=?", response.get("id"));
        assertEquals(teacherId, row.get("created_by"));
        assertEquals("Algebra final", row.get("title"));
        assertEquals("Mathematics", row.get("subject"));
        assertEquals(45, row.get("duration_minutes"));
        assertEquals(new BigDecimal("40.00"), row.get("total_marks"));
        assertEquals(new BigDecimal("24.00"), row.get("passing_marks"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_id=? AND action='EXAM_CREATE' AND entity_id=?",
                Integer.class, teacherId, response.get("id").toString()));
        Timestamp persistedStart = jdbc.queryForObject("SELECT starts_at FROM exams WHERE id=?", Timestamp.class, response.get("id"));
        assertEquals(start, persistedStart.toInstant());
    }

    @Test
    void rejectsInvalidWindowAndPassingMarksWithoutPostgresWrites() {
        var invalidWindow = request("Algebra final", "Mathematics", 45, start, start,
                new BigDecimal("40"), new BigDecimal("24"));
        var invalidPassing = request("Algebra final", "Mathematics", 45, start, start.plusSeconds(7200),
                new BigDecimal("20"), new BigDecimal("24"));

        assertTrue(assertThrows(ResponseStatusException.class,
                () -> controller.createExam(invalidWindow, teacher)).getReason().contains("end must be after"));
        assertTrue(assertThrows(ResponseStatusException.class,
                () -> controller.createExam(invalidPassing, teacher)).getReason().contains("cannot exceed total"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM exams WHERE created_by=?", Integer.class, teacherId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_id=?", Integer.class, teacherId));
    }

    private DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    private ExamController.ExamRequest request(String title, String subject, int duration, Instant startsAt, Instant endsAt,
                                               BigDecimal total, BigDecimal passing) {
        return new ExamController.ExamRequest(title, subject, duration, startsAt, endsAt, total, passing,
                0, false, 1, false, false);
    }
}
