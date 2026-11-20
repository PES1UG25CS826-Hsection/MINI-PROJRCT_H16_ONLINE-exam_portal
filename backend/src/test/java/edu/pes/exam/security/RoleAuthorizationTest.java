package edu.pes.exam.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pes.exam.admin.AdminController;
import edu.pes.exam.exam.ExamController;
import edu.pes.exam.exam.ExamScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class RoleAuthorizationTest {
    @Test
    void studentCannotInvokeTeacherOrAdminControllerMethods() {
        try (var context = new AnnotationConfigApplicationContext(MethodSecurityTestConfig.class)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "student", "n/a", java.util.List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
            AdminController admin = context.getBean(AdminController.class);
            ExamController exams = context.getBean(ExamController.class);

            assertThrows(AccessDeniedException.class, admin::summary);
            assertThrows(AccessDeniedException.class, () -> exams.createExam(null, null));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Configuration
    @EnableMethodSecurity
    @Import({AdminController.class, ExamController.class})
    static class MethodSecurityTestConfig {
        @Bean JdbcTemplate jdbcTemplate() { return mock(JdbcTemplate.class); }
        @Bean PasswordEncoder passwordEncoder() { return mock(PasswordEncoder.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean ExamScoringService examScoringService() { return new ExamScoringService(); }
    }
}
