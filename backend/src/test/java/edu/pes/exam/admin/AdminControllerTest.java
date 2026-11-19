package edu.pes.exam.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import edu.pes.exam.security.PortalPrincipal;

class AdminControllerTest {
    private JdbcTemplate jdbc;
    private PasswordEncoder passwords;
    private AdminController controller;
    private final PortalPrincipal admin = new PortalPrincipal(UUID.randomUUID(), "admin@example.test", "ADMIN");

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        passwords = mock(PasswordEncoder.class);
        controller = new AdminController(jdbc, passwords);
    }

    @Test
    void rejectsUnknownRolesAndWeakPasswordsBeforeWriting() {
        var unknownRole = assertThrows(ResponseStatusException.class, () -> controller.createUser(
                new AdminController.CreateUser("Name", "person@example.test", "StrongPass1!", "OBSERVER"), admin));
        assertEquals(400, unknownRole.getStatusCode().value());
        var weakPassword = assertThrows(ResponseStatusException.class, () -> controller.createUser(
                new AdminController.CreateUser("Name", "person@example.test", "weak", "STUDENT"), admin));
        assertEquals(400, weakPassword.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test
    void normalizesAndPersistsUserAndWritesAuditRecord() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(passwords.encode("StrongPass1!")).thenReturn("hashed");
        var result = controller.createUser(new AdminController.CreateUser("  Learner  ", " Student@Example.Test ",
                "StrongPass1!", " student "), admin);
        assertEquals("student@example.test", result.get("email"));
        assertEquals("STUDENT", result.get("role"));
        assertEquals(true, result.get("active"));
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
        verify(passwords).encode("StrongPass1!");
    }

    @Test
    void rejectsDuplicateEmailAndReturnsPersistedSummaryCounts() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        var duplicate = assertThrows(ResponseStatusException.class, () -> controller.createUser(
                new AdminController.CreateUser("Learner", "student@example.test", "StrongPass1!", "STUDENT"), admin));
        assertEquals(409, duplicate.getStatusCode().value());

        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(8L, 3L, 14L, 5L);
        var summary = controller.summary();
        assertEquals(8L, summary.get("users"));
        assertEquals(3L, summary.get("exams"));
        assertEquals(14L, summary.get("attempts"));
        assertEquals(5L, summary.get("submittedAttempts"));
    }
}