package edu.pes.exam.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {
    @Test
    void bearerFilterRunsOnlyInsideSpringSecurityChain() {
        SecurityConfig config = new SecurityConfig();

        FilterRegistrationBean<BearerTokenFilter> registration =
                config.bearerTokenFilterRegistration(mock(BearerTokenFilter.class));

        assertFalse(registration.isEnabled());
    }
}
