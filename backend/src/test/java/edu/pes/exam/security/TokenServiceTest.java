package edu.pes.exam.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TokenServiceTest {
    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac-sha256";
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private TokenService tokens;

    @SuppressWarnings("unchecked")
    @BeforeEach void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        tokens = new TokenService(SECRET, redis);
    }

    @Test void verifiesIssuedUserAndRoleAndRefreshesIdleExpiry() {
        UUID id=UUID.randomUUID();
        String token = tokens.issue(id, "STUDENT");
        when(values.get("portal:session:" + token)).thenReturn(id.toString());
        TokenService.Principal principal=tokens.verify(token);
        assertEquals(id,principal.userId());
        assertEquals("STUDENT",principal.role());
        verify(values).set("portal:session:" + token, id.toString(), Duration.ofMinutes(30));
        verify(redis).expire("portal:session:" + token, Duration.ofMinutes(30));
    }

    @Test void rejectsSessionAfterIdleExpiry() {
        String token = tokens.issue(UUID.randomUUID(), "STUDENT");
        when(values.get("portal:session:" + token)).thenReturn(null);
        assertNull(tokens.verify(token));
    }

    @Test void rejectsTamperedAndMalformedTokens() {
        String token=tokens.issue(UUID.randomUUID(),"STUDENT");
        assertNull(tokens.verify(token+"x"));
        assertNull(tokens.verify("not-a-token"));
    }

    @Test void logoutRevokesSessionAndRejectsReplay() {
        UUID id = UUID.randomUUID();
        String token = tokens.issue(id, "STUDENT");
        when(values.get("portal:session:" + token)).thenReturn(null);
        tokens.revoke(token);
        verify(redis).delete("portal:session:" + token);
        assertNull(tokens.verify(token));
    }
}
