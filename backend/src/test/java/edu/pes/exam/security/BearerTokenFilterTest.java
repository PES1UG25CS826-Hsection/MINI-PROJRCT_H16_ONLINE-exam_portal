package edu.pes.exam.security;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BearerTokenFilterTest {
    private final TokenService tokens = mock(TokenService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesBearerTokenWhenActiveUserIsFound() throws Exception {
        UUID id = UUID.randomUUID();
        PortalPrincipal principal = new PortalPrincipal(id, "student@example.test", "STUDENT");
        when(tokens.userId("opaque-token")).thenReturn(id.toString());
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<PortalPrincipal>>any(), eq(id)))
                .thenReturn(List.of(principal));
        AtomicBoolean chainCalled = new AtomicBoolean();
        FilterChain chain = (request, response) -> chainCalled.set(true);

        new BearerTokenFilter(tokens, jdbc).doFilter(
                request("Bearer opaque-token"), new MockHttpServletResponse(), chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertSame(principal, authentication.getPrincipal());
        assertTrue(authentication.isAuthenticated());
        assertEquals(List.of("ROLE_STUDENT"), authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).toList());
        assertTrue(chainCalled.get());
        verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<PortalPrincipal>>any(), eq(id));
    }

    @Test
    void leavesRequestUnauthenticatedWhenSessionIsMissing() throws Exception {
        when(tokens.userId("expired-token")).thenReturn(null);
        AtomicBoolean chainCalled = new AtomicBoolean();
        FilterChain chain = (request, response) -> chainCalled.set(true);

        new BearerTokenFilter(tokens, jdbc).doFilter(
                request("Bearer expired-token"), new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(chainCalled.get());
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        return request;
    }
}
