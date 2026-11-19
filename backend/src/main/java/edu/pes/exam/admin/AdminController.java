package edu.pes.exam.admin;

import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import edu.pes.exam.security.PortalPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AdminController(JdbcTemplate jdbc,PasswordEncoder passwords) { this.jdbc = jdbc;this.passwords=passwords; }

    public record CreateUser(@NotBlank @Size(max=160) String fullName,@Email @NotBlank @Size(max=254) String email,
                             @NotBlank @Size(min=8,max=128) String password,@NotBlank String role) { }

    @GetMapping("/users")
    public List<Map<String,Object>> users() {
        return jdbc.query("SELECT id,full_name,email,role,is_active,created_at FROM users ORDER BY created_at DESC",
                (rs,n)->Map.<String,Object>of("id",rs.getObject("id",UUID.class),"fullName",rs.getString("full_name"),"email",rs.getString("email"),"role",rs.getString("role"),"active",rs.getBoolean("is_active"),"createdAt",rs.getTimestamp("created_at").toInstant()));
    }

    @PostMapping("/users")
    public Map<String,Object> createUser(@Valid @RequestBody CreateUser request,@AuthenticationPrincipal PortalPrincipal actor) {
        String role=request.role().trim().toUpperCase(Locale.ROOT);
        if(!List.of("STUDENT","TEACHER","ADMIN").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Role must be STUDENT, TEACHER, or ADMIN.");
        if(!Pattern.compile("^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,128}$").matcher(request.password()).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be at least 8 characters and include uppercase, a number, and a symbol.");
        String email=request.email().trim().toLowerCase(Locale.ROOT);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Integer.class,email)>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists.");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,full_name,role) VALUES (?,?,?,?,?)",id,email,passwords.encode(request.password()),request.fullName().trim(),role);
        jdbc.update("INSERT INTO audit_log(actor_id,action,entity_type,entity_id) VALUES (?,'USER_CREATE','USER',?)",actor.id(),id.toString());
        return Map.of("id",id,"email",email,"role",role,"active",true);
    }

    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return Map.of(
                "users", jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE is_active=TRUE", Long.class),
                "exams", jdbc.queryForObject("SELECT COUNT(*) FROM exams", Long.class),
                "attempts", jdbc.queryForObject("SELECT COUNT(*) FROM attempts", Long.class),
                "submittedAttempts", jdbc.queryForObject("SELECT COUNT(*) FROM attempts WHERE status IN ('SUBMITTED','TIMED_OUT')", Long.class));
    }
}
