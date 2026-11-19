package edu.pes.exam.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class BearerTokenFilterTest {
    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void authenticatesActiveUserFromBearerToken() throws Exception {
        UUID id = UUID.randomUUID();
        RecordingJdbc jdbc = new RecordingJdbc(id, "student@example.test", "STUDENT");
        BearerTokenFilter filter = new BearerTokenFilter(new FixedTokenService(id.toString()), jdbc);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CountingChain chain = new CountingChain();

        filter.doFilter(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(jdbc.queried);
        assertTrue(chain.called);
        assertEquals(new PortalPrincipal(id, "student@example.test", "STUDENT"), auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT")));
    }

    @Test
    void leavesRequestUnauthenticatedForInvalidSessionId() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc(UUID.randomUUID(), "nobody@example.test", "STUDENT");
        BearerTokenFilter filter = new BearerTokenFilter(new FixedTokenService("not-a-uuid"), jdbc);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        CountingChain chain = new CountingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertFalse(jdbc.queried);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(chain.called);
    }

    @Test
    void skipsDatabaseLookupWhenNoBearerHeaderIsPresent() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc(UUID.randomUUID(), "nobody@example.test", "STUDENT");
        BearerTokenFilter filter = new BearerTokenFilter(new FixedTokenService(UUID.randomUUID().toString()), jdbc);
        CountingChain chain = new CountingChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertFalse(jdbc.queried);
        assertTrue(chain.called);
    }

    private static final class FixedTokenService extends TokenService {
        private final String userId;
        FixedTokenService(String userId) { super(null); this.userId = userId; }
        @Override public String userId(String token) { return userId; }
    }

    private static final class RecordingJdbc extends JdbcTemplate {
        private final UUID id;
        private final String email;
        private final String role;
        private boolean queried;

        RecordingJdbc(UUID id, String email, String role) {
            this.id = id; this.email = email; this.role = role;
        }

        @Override
        public void query(String sql, RowCallbackHandler handler, Object... args) {
            queried = true;
            ResultSet rs = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class}, new ResultSetValues(id, email, role));
            try { handler.processRow(rs); }
            catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
        }
    }

    private static final class ResultSetValues implements java.lang.reflect.InvocationHandler {
        private final UUID id;
        private final String email;
        private final String role;
        private boolean consumed;

        ResultSetValues(UUID id, String email, String role) {
            this.id = id; this.email = email; this.role = role;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return switch (method.getName()) {
                case "next" -> { boolean next = !consumed; consumed = true; yield next; }
                case "getObject" -> id;
                case "getString" -> "email".equals(args[0]) ? email : role;
                default -> null;
            };
        }
    }

    private static final class CountingChain implements FilterChain {
        private boolean called;
        @Override public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            called = true;
        }
    }
}
