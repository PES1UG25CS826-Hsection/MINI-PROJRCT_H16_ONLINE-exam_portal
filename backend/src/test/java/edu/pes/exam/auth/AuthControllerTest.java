package edu.pes.exam.auth;

import java.util.Map;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import edu.pes.exam.security.PortalPrincipal;
import edu.pes.exam.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final TokenService tokens = mock(TokenService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AuthController controller = new AuthController(jdbc, passwords, tokens, redis);
    private ValueOperations<String, String> values;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void configureRedisValues() {
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void registrationCreatesStudentWithHashedPasswordAndRequiresSignIn() {
        when(passwords.encode("SecurePass1!")).thenReturn("bcrypt-hash");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        var response = controller.register(new AuthController.RegisterRequest(
                "  Demo Student ", "Student@Example.Test", "SecurePass1!"), request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertEquals("Registration successful. Sign in with your new account.", body.get("message"));
        Map<?, ?> user = (Map<?, ?>) body.get("user");
        assertEquals("student@example.test", user.get("email"));
        assertEquals("STUDENT", user.get("role"));
        assertFalse(body.containsKey("token"));
        verify(passwords).encode("SecurePass1!");
        verify(jdbc).update(contains("VALUES (?,?,?,?, 'STUDENT') ON CONFLICT (email) DO NOTHING"), any(Object[].class));
        verify(tokens, never()).issue(any());
    }

    @Test
    void duplicateRegistrationReturnsConflictIncludingConcurrentInsertRace() {
        when(passwords.encode("SecurePass1!")).thenReturn("bcrypt-hash");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        HttpServletRequest request = mock(HttpServletRequest.class);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.register(
                new AuthController.RegisterRequest("Demo Student", "student@example.test", "SecurePass1!"), request));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("An account with this email already exists.", error.getReason());
        verify(jdbc, org.mockito.Mockito.times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    void weakPasswordIsRejectedBeforeDatabaseWrite() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.register(
                new AuthController.RegisterRequest("Demo Student", "student@example.test", "password"), request));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(passwords, never()).encode(anyString());
    }

    @Test
    void loginEnforcesIpRateLimitAndTemporaryAccountLock() {
        when(values.increment(org.mockito.ArgumentMatchers.startsWith("portal:login:ip:"))).thenReturn(6L, 1L);
        when(redis.hasKey(anyString())).thenReturn(true);
        HttpServletRequest request = request();

        ResponseStatusException rateLimited = assertThrows(ResponseStatusException.class,
                () -> controller.login(new AuthController.LoginRequest("student@example.test", "SecurePass1!"), request));
        ResponseStatusException locked = assertThrows(ResponseStatusException.class,
                () -> controller.login(new AuthController.LoginRequest("student@example.test", "SecurePass1!"), request));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rateLimited.getStatusCode());
        assertEquals(HttpStatus.LOCKED, locked.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void loginRejectsUnknownUsersAndLocksAfterFifthFailure() {
        when(values.increment(org.mockito.ArgumentMatchers.startsWith("portal:login:ip:"))).thenReturn(1L, 1L);
        when(values.increment(org.mockito.ArgumentMatchers.startsWith("portal:login:account:"))).thenReturn(1L, 5L);
        when(redis.hasKey(anyString())).thenReturn(false);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        ResponseStatusException invalid = assertThrows(ResponseStatusException.class,
                () -> controller.login(new AuthController.LoginRequest("missing@example.test", "WrongPass1!"), request()));
        ResponseStatusException locked = assertThrows(ResponseStatusException.class,
                () -> controller.login(new AuthController.LoginRequest("missing@example.test", "WrongPass1!"), request()));

        assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
        assertEquals(HttpStatus.LOCKED, locked.getStatusCode());
        verify(values).set(contains(":locked"), eq("1"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void loginIssuesSessionForActiveAccountAndClearsFailureCounter() throws Exception {
        UUID userId = UUID.randomUUID();
        when(values.increment(org.mockito.ArgumentMatchers.startsWith("portal:login:ip:"))).thenReturn(1L);
        when(redis.hasKey(anyString())).thenReturn(false);
        when(passwords.matches("SecurePass1!", "bcrypt-hash")).thenReturn(true);
        when(tokens.issue(userId, "STUDENT")).thenReturn("active-session");
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet row = mock(ResultSet.class);
                    when(row.getObject("id", UUID.class)).thenReturn(userId);
                    when(row.getString("email")).thenReturn("student@example.test");
                    when(row.getString("password_hash")).thenReturn("bcrypt-hash");
                    when(row.getString("role")).thenReturn("STUDENT");
                    when(row.getString("full_name")).thenReturn("Demo Student");
                    when(row.getBoolean("is_active")).thenReturn(true);
                    return List.of(mapper.mapRow(row, 0));
                });

        var response = controller.login(new AuthController.LoginRequest(" Student@Example.Test ", "SecurePass1!"), request());

        assertEquals("active-session", response.get("token"));
        assertEquals("STUDENT", ((Map<?, ?>) response.get("user")).get("role"));
        verify(redis).delete("portal:login:account:student_example.test:failures");
    }

    @Test
    void logoutRevokesBearerSessionAndMeReturnsCurrentPrincipal() {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer opaque-token");
        PortalPrincipal principal = new PortalPrincipal(userId, "student@example.test", "STUDENT");

        assertEquals("Signed out.", controller.logout(request, principal).get("message"));
        assertEquals("STUDENT", controller.me(principal).get("role"));
        verify(tokens).revoke("opaque-token");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
