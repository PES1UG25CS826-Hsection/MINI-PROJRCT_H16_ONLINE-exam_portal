package edu.pes.exam.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TokenServiceTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private TokenService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new TokenService(redis);
    }

    @Test
    void issueCreatesOpaqueTokenAndStoresUserWithIdleTtl() {
        UUID id = UUID.randomUUID();
        String token = service.issue(id);
        assertFalse(token.isBlank());
        assertFalse(token.contains("="));
        verify(values).set("portal:session:" + token, id.toString(), Duration.ofMinutes(30));
    }

    @Test
    void userIdRefreshesIdleTtlOnlyForExistingSession() {
        when(values.get("portal:session:present")).thenReturn("user-id");
        assertEquals("user-id", service.userId("present"));
        verify(redis).expire("portal:session:present", Duration.ofMinutes(30));
        when(values.get("portal:session:missing")).thenReturn(null);
        assertNull(service.userId("missing"));
        verify(redis, never()).expire(eq("portal:session:missing"), any(Duration.class));
    }

    @Test
    void revokeDeletesSessionKey() {
        service.revoke("session-token");
        verify(redis).delete("portal:session:session-token");
    }
}