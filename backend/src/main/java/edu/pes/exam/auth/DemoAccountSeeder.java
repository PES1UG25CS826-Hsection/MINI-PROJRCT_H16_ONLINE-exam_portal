package edu.pes.exam.auth;

import java.util.Locale;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoAccountSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Environment environment;

    public DemoAccountSeeder(JdbcTemplate jdbc, PasswordEncoder passwords, Environment environment) {
        this.jdbc = jdbc; this.passwords = passwords; this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.getProperty("DEMO_ACCOUNTS_ENABLED", Boolean.class, false)) return;
        seed("ADMIN", "DEMO_ADMIN_EMAIL", "DEMO_ADMIN_PASSWORD", "Demo Administrator");
        seed("TEACHER", "DEMO_TEACHER_EMAIL", "DEMO_TEACHER_PASSWORD", "Demo Teacher");
        seed("STUDENT", "DEMO_STUDENT_EMAIL", "DEMO_STUDENT_PASSWORD", "Demo Student");
    }

    private void seed(String role, String emailKey, String passwordKey, String name) {
        String email = environment.getProperty(emailKey, "").trim().toLowerCase(Locale.ROOT);
        String password = environment.getProperty(passwordKey, "");
        if (email.isBlank() || password.isBlank()) return;
        if (password.length() < 12) throw new IllegalStateException(passwordKey + " must be at least 12 characters when configured.");
        jdbc.update("INSERT INTO users(id,email,password_hash,full_name,role) VALUES (?,?,?,?,?) ON CONFLICT (email) DO NOTHING",
                UUID.randomUUID(), email, passwords.encode(password), name, role);
    }
}
