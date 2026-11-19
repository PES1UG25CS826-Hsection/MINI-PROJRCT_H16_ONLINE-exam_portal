package edu.pes.exam.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockHttpServletRequest;

import edu.pes.exam.security.PortalPrincipal;
import edu.pes.exam.security.TokenService;

class AuthControllerTest {
    private JdbcTemplate jdbc;
    private PasswordEncoder passwords;
    private TokenService tokens;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private AuthController controller;
    private final UUID userId = UUID.fromString("4b3be126-2e3b-4e8b-a3d2-18601858660a");

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        passwords = mock(PasswordEncoder.class);
        tokens = mock(TokenService.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        controller = new AuthController(jdbc, passwords, tokens, redis);
    }

    @Test
    void registrationRejectsWeakPasswordBeforeDatabaseAccess() {
        var error = assertThrows(ResponseStatusException.class, () -> controller.register(
                new AuthController.RegisterRequest("Learner", "a@example.test", "weakpass"), request()));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test
    void registrationRejectsDuplicateEmailAndNormalizesSuccessfulAccount() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0);
        var duplicate = assertThrows(ResponseStatusException.class, () -> controller.register(
                new AuthController.RegisterRequest("Learner", "a@example.test", "GoodPass1!"), request()));
        assertEquals(409, duplicate.getStatusCode().value());

        when(passwords.encode("GoodPass1!")).thenReturn("encoded");
        when(tokens.issue(any(UUID.class))).thenReturn("session-token");
        var response = controller.register(new AuthController.RegisterRequest(" Learner ", " A@Example.Test ", "GoodPass1!"), request());
        assertEquals("a@example.test", response.get("user") instanceof java.util.Map<?, ?> user ? user.get("email") : null);
        assertEquals("session-token", response.get("token"));
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void loginAppliesIpLimitAndAccountLock() {
        when(values.increment(startsWith("portal:login:ip:"))).thenReturn(6L);
        var ipLimit = assertThrows(ResponseStatusException.class, () -> controller.login(
                new AuthController.LoginRequest("a@example.test", "GoodPass1!"), request()));
        assertEquals(429, ipLimit.getStatusCode().value());
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(Object[].class));

        when(values.increment(anyString())).thenReturn(1L);
        when(redis.hasKey(contains(":locked"))).thenReturn(true);
        var locked = assertThrows(ResponseStatusException.class, () -> controller.login(
                new AuthController.LoginRequest("a@example.test", "GoodPass1!"), request()));
        assertEquals(423, locked.getStatusCode().value());
    }

    @Test
    void loginRejectsUnknownAccountAndLocksAfterFifthFailure() {
        when(values.increment(anyString())).thenReturn(1L, 1L, 1L, 5L);
        when(redis.hasKey(anyString())).thenReturn(false);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        var unauthorized = assertThrows(ResponseStatusException.class, () -> controller.login(
                new AuthController.LoginRequest("missing@example.test", "WrongPass1!"), request()));
        assertEquals(401, unauthorized.getStatusCode().value());
        var locked = assertThrows(ResponseStatusException.class, () -> controller.login(
                new AuthController.LoginRequest("missing@example.test", "WrongPass1!"), request()));
        assertEquals(423, locked.getStatusCode().value());
        verify(values).set(contains(":locked"), eq("1"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void loginCreatesSessionForActiveAccountAndClearsFailureCounter() throws Exception {
        when(values.increment(startsWith("portal:login:ip:"))).thenReturn(1L);
        when(redis.hasKey(anyString())).thenReturn(false);
        when(passwords.matches("GoodPass1!", "encoded")).thenReturn(true);
        when(tokens.issue(userId)).thenReturn("active-session");
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet row = mock(ResultSet.class);
            when(row.getObject("id", UUID.class)).thenReturn(userId);
            when(row.getString("email")).thenReturn("a@example.test");
            when(row.getString("password_hash")).thenReturn("encoded");
            when(row.getString("role")).thenReturn("TEACHER");
            when(row.getBoolean("is_active")).thenReturn(true);
            return List.of(mapper.mapRow(row, 0));
        });
        var response = controller.login(new AuthController.LoginRequest(" A@Example.Test ", "GoodPass1!"), request());
        assertEquals("active-session", response.get("token"));
        verify(redis).delete("portal:login:account:a_example.test:failures");
        verify(jdbc, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    void logoutRevokesBearerAndMeReturnsPrincipal() {
        MockHttpServletRequest http = request();
        http.addHeader("Authorization", "Bearer token-value");
        PortalPrincipal principal = new PortalPrincipal(userId, "a@example.test", "STUDENT");
        assertEquals("Signed out.", controller.logout(http, principal).get("message"));
        verify(tokens).revoke("token-value");
        assertEquals("STUDENT", controller.me(principal).get("role"));
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}