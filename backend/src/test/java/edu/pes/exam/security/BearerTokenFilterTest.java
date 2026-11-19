package edu.pes.exam.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;

class BearerTokenFilterTest {
    private TokenService tokens;
    private JdbcTemplate jdbc;
    private BearerTokenFilter filter;

    @BeforeEach
    void setUp() {
        tokens = mock(TokenService.class);
        jdbc = mock(JdbcTemplate.class);
        filter = new BearerTokenFilter(tokens, jdbc);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void missingBearerTokenPassesThroughUnauthenticated() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(tokens, jdbc);
    }

    @Test
    void unknownTokenAndMalformedUserIdRemainUnauthenticated() throws Exception {
        MockHttpServletRequest unknown = new MockHttpServletRequest();
        unknown.addHeader("Authorization", "Bearer unknown");
        when(tokens.userId("unknown")).thenReturn(null);
        filter.doFilterInternal(unknown, new MockHttpServletResponse(), new MockFilterChain());
        verifyNoInteractions(jdbc);

        MockHttpServletRequest malformed = new MockHttpServletRequest();
        malformed.addHeader("Authorization", "Bearer malformed");
        when(tokens.userId("malformed")).thenReturn("not-a-uuid");
        filter.doFilterInternal(malformed, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jdbc);
    }

    @Test
    void missingActiveUserLeavesRequestUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-format");
        when(tokens.userId("valid-format")).thenReturn(UUID.randomUUID().toString());
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());
        verify(jdbc).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class), any(Object[].class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}