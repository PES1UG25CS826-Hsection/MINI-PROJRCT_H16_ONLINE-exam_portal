package edu.pes.exam.security;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final JdbcTemplate jdbc;

    public BearerTokenFilter(TokenService tokens, JdbcTemplate jdbc) {
        this.tokens = tokens;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, java.io.IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String id = tokens.userId(header.substring(7).trim());
            if (id != null) {
                try {
                    jdbc.query("SELECT id,email,role FROM users WHERE id=? AND is_active=TRUE",
                            rs -> {
                                if (rs.next()) {
                                    PortalPrincipal principal = new PortalPrincipal(rs.getObject("id", UUID.class),
                                            rs.getString("email"), rs.getString("role"));
                                    var auth = new UsernamePasswordAuthenticationToken(principal, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                                    SecurityContextHolder.getContext().setAuthentication(auth);
                                }
                            }, UUID.fromString(id));
                } catch (IllegalArgumentException ignored) {
                    // Invalid or expired bearer tokens remain unauthenticated.
                }
            }
        }
        chain.doFilter(request, response);
    }
}
