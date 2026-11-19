package edu.pes.exam.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
    private static final long LIFETIME_SECONDS = 12 * 60 * 60;
    private final byte[] secret;

    public TokenService(@Value("${security.token-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) throw new IllegalArgumentException("security.token-secret must be at least 32 characters");
    }

    public String issue(UUID userId, String role) {
        String payload = userId + ":" + role + ":" + (Instant.now().getEpochSecond() + LIFETIME_SECONDS);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    public Principal verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.US_ASCII), parts[1].getBytes(StandardCharsets.US_ASCII))) return null;
            String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split(":", -1);
            if (fields.length != 3 || Long.parseLong(fields[2]) < Instant.now().getEpochSecond()) return null;
            return new Principal(UUID.fromString(fields[0]), fields[1]);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign access token", ex);
        }
    }

    public record Principal(UUID userId, String role) {}
}
