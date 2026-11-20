package edu.pes.exam.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {
    @Test
    void bearerFilterRunsOnlyInsideSpringSecurityChain() {
        SecurityConfig config = new SecurityConfig();

        FilterRegistrationBean<BearerTokenFilter> registration =
                config.bearerTokenFilterRegistration(mock(BearerTokenFilter.class));

        assertFalse(registration.isEnabled());
    }

    @Test
    void securityFilterChainBuildsWithPublicErrorDispatchHandling() {
        try (var context = new AnnotationConfigApplicationContext(WebSecurityTestConfig.class)) {
            assertNotNull(context.getBean(SecurityFilterChain.class));
        }
    }

    @Configuration
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class WebSecurityTestConfig {
        @Bean BearerTokenFilter bearerTokenFilter() { return mock(BearerTokenFilter.class); }
    }
}
