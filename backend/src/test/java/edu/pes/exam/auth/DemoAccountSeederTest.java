package edu.pes.exam.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoAccountSeederTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final Environment environment = mock(Environment.class);

    @Test
    void demoSeedingIsDisabledUnlessExplicitlyEnabled() {
        when(environment.getProperty("DEMO_ACCOUNTS_ENABLED", Boolean.class, false)).thenReturn(false);

        new DemoAccountSeeder(jdbc, passwords, environment).run(new DefaultApplicationArguments(new String[0]));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(passwords, never()).encode(anyString());
    }

    @Test
    void explicitlyEnabledSeedsHashedCredentialsForAllRolesWithConflictProtection() {
        when(environment.getProperty("DEMO_ACCOUNTS_ENABLED", Boolean.class, false)).thenReturn(true);
        for (String role : new String[] {"ADMIN", "TEACHER", "STUDENT"}) {
            when(environment.getProperty("DEMO_" + role + "_EMAIL", "")).thenReturn(role.toLowerCase() + "@example.test");
            when(environment.getProperty("DEMO_" + role + "_PASSWORD", "")).thenReturn("SecurePass1!" + role);
        }
        when(passwords.encode(anyString())).thenReturn("bcrypt-hash");

        new DemoAccountSeeder(jdbc, passwords, environment).run(new DefaultApplicationArguments(new String[0]));

        verify(jdbc, times(3)).update(contains("ON CONFLICT (email) DO NOTHING"), any(Object[].class));
        verify(passwords, times(3)).encode(anyString());
    }

    @Test
    void enabledDemoSeedRejectsShortConfiguredPassword() {
        when(environment.getProperty("DEMO_ACCOUNTS_ENABLED", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("DEMO_ADMIN_EMAIL", "")).thenReturn("admin@example.test");
        when(environment.getProperty("DEMO_ADMIN_PASSWORD", "")).thenReturn("short");

        assertThrows(IllegalStateException.class,
                () -> new DemoAccountSeeder(jdbc, passwords, environment).run(new DefaultApplicationArguments(new String[0])));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
