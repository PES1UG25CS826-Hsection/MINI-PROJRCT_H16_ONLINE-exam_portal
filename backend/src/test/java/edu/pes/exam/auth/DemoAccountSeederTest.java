package edu.pes.exam.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class DemoAccountSeederTest {
    private JdbcTemplate jdbc;
    private PasswordEncoder passwords;
    private Environment environment;
    private DemoAccountSeeder seeder;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        passwords = mock(PasswordEncoder.class);
        environment = mock(Environment.class);
        seeder = new DemoAccountSeeder(jdbc, passwords, environment);
    }

    @Test
    void skipsAccountsWhenCredentialsAreNotConfigured() {
        when(environment.getProperty(anyString(), eq(""))).thenReturn("");
        seeder.run(new DefaultApplicationArguments(new String[0]));
        verifyNoInteractions(jdbc);
    }

    @Test
    void requiresTwelveCharacterSeedPassword() {
        when(environment.getProperty("DEMO_ADMIN_EMAIL", "")).thenReturn("admin@example.test");
        when(environment.getProperty("DEMO_ADMIN_PASSWORD", "")).thenReturn("short");
        var error = assertThrows(IllegalStateException.class, () -> seeder.run(new DefaultApplicationArguments(new String[0])));
        assertTrue(error.getMessage().contains("DEMO_ADMIN_PASSWORD"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void insertsOnlyConfiguredMissingAccounts() {
        when(environment.getProperty("DEMO_ADMIN_EMAIL", "")).thenReturn(" Admin@Example.Test ");
        when(environment.getProperty("DEMO_ADMIN_PASSWORD", "")).thenReturn("StrongAdmin1!");
        when(environment.getProperty("DEMO_TEACHER_EMAIL", "")).thenReturn("teacher@example.test");
        when(environment.getProperty("DEMO_TEACHER_PASSWORD", "")).thenReturn("StrongTeacher1!");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0, 1);
        when(passwords.encode(anyString())).thenAnswer(i -> "encoded:" + i.getArgument(0));
        seeder.run(new DefaultApplicationArguments(new String[0]));
        verify(jdbc, times(1)).update(anyString(), any(Object[].class));
        verify(passwords).encode("StrongAdmin1!");
        verify(passwords, never()).encode("StrongTeacher1!");
    }
}