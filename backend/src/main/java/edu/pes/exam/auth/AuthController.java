package edu.pes.exam.auth;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import edu.pes.exam.security.PortalPrincipal;
import edu.pes.exam.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,128}$");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final StringRedisTemplate redis;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwords, TokenService tokens, StringRedisTemplate redis) {
        this.jdbc = jdbc; this.passwords = passwords; this.tokens = tokens; this.redis = redis;
    }

    public record RegisterRequest(@NotBlank @Size(max = 160) String fullName,
                                  @Email @NotBlank @Size(max = 254) String email,
                                  @NotBlank @Size(min = 8, max = 128) String password) { }
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) { }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        if (!PASSWORD.matcher(request.password()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and include an uppercase letter, a number, and a symbol.");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?", Integer.class, email) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,full_name,role) VALUES (?,?,?,?, 'STUDENT')",
                id, email, passwords.encode(request.password()), request.fullName().trim());
        audit(id, "REGISTER", "USER", id.toString(), http.getRemoteAddr());
        return session(id, email, "STUDENT");
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ipKey = "portal:login:ip:" + safeKey(http.getRemoteAddr());
        Long hits = redis.opsForValue().increment(ipKey);
        if (hits != null && hits == 1) redis.expire(ipKey, java.time.Duration.ofMinutes(1));
        if (hits != null && hits > 5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again in a minute.");

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String accountKey = "portal:login:account:" + safeKey(email);
        if (Boolean.TRUE.equals(redis.hasKey(accountKey + ":locked"))) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked. Try again in 15 minutes.");
        }
        var rows = jdbc.query("SELECT id,email,password_hash,role,is_active FROM users WHERE email=?",
                (rs, row) -> new Account(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("password_hash"), rs.getString("role"), rs.getBoolean("is_active")), email);
        Account account = rows.isEmpty() ? null : rows.get(0);
        boolean accepted = account != null && account.active() && passwords.matches(request.password(), account.hash());
        if (!accepted) {
            long failures = redis.opsForValue().increment(accountKey + ":failures");
            if (failures == 1) redis.expire(accountKey + ":failures", java.time.Duration.ofMinutes(15));
            if (failures >= 5) {
                redis.opsForValue().set(accountKey + ":locked", "1", java.time.Duration.ofMinutes(15));
                redis.delete(accountKey + ":failures");
                throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked. Try again in 15 minutes.");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        redis.delete(accountKey + ":failures");
        audit(account.id(), "LOGIN", "USER", account.id().toString(), http.getRemoteAddr());
        return session(account.id(), account.email(), account.role());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request, @AuthenticationPrincipal PortalPrincipal user) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) tokens.revoke(header.substring(7).trim());
        audit(user.id(), "LOGOUT", "USER", user.id().toString(), request.getRemoteAddr());
        return Map.of("message", "Signed out.");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal PortalPrincipal user) {
        return Map.of("id", user.id(), "email", user.email(), "role", user.role());
    }

    private Map<String, Object> session(UUID id, String email, String role) {
        return Map.of("token", tokens.issue(id), "user", Map.of("id", id, "email", email, "role", role));
    }
    private void audit(UUID actor, String action, String type, String entity, String ip) {
        jdbc.update("INSERT INTO audit_log(actor_id,action,entity_type,entity_id,ip_address) VALUES (?,?,?,?,?::inet)",
                actor, action, type, entity, ip);
    }
    private String safeKey(String value) { return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9_.-]", "_"); }
    private record Account(UUID id, String email, String hash, String role, boolean active) { }
}
