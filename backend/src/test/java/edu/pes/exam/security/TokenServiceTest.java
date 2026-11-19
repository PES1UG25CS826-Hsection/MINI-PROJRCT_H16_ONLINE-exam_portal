package edu.pes.exam.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TokenServiceTest {
    @Test
    void issuesRandomUrlSafeTokenAndStoresSessionWithIdleTtl() {
        RecordingRedis redis = new RecordingRedis();
        UUID userId = UUID.randomUUID();

        String token = new TokenService(redis).issue(userId);

        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
        assertEquals("portal:session:" + token, redis.lastKey);
        assertEquals(userId.toString(), redis.values.get(redis.lastKey));
        assertEquals(Duration.ofMinutes(30), redis.savedTtl);
    }

    @Test
    void refreshesIdleTtlWhenSessionExists() {
        RecordingRedis redis = new RecordingRedis();
        String token = "session-token";
        String key = "portal:session:" + token;
        String userId = UUID.randomUUID().toString();
        redis.values.put(key, userId);

        assertEquals(userId, new TokenService(redis).userId(token));

        assertEquals(key, redis.expiredKey);
        assertEquals(Duration.ofMinutes(30), redis.expiry);
    }

    @Test
    void doesNotRefreshTtlForMissingSession() {
        RecordingRedis redis = new RecordingRedis();

        assertNull(new TokenService(redis).userId("expired-token"));

        assertNull(redis.expiredKey);
    }

    @Test
    void revokesSessionByDeletingItsRedisKey() {
        RecordingRedis redis = new RecordingRedis();
        String token = "session-token";

        new TokenService(redis).revoke(token);

        assertEquals("portal:session:" + token, redis.deletedKey);
    }

    private static final class RecordingRedis extends StringRedisTemplate {
        private final Map<String, String> values = new HashMap<>();
        private final ValueOperations<String, String> operations;
        private String lastKey;
        private Duration savedTtl;
        private String expiredKey;
        private Duration expiry;
        private String deletedKey;

        @SuppressWarnings("unchecked")
        RecordingRedis() {
            operations = (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(), new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("set") && args.length == 3) {
                            lastKey = (String) args[0];
                            values.put(lastKey, (String) args[1]);
                            savedTtl = (Duration) args[2];
                            return null;
                        }
                        if (method.getName().equals("get")) return values.get(args[0]);
                        return null;
                    });
        }

        @Override
        public ValueOperations<String, String> opsForValue() { return operations; }

        @Override
        public Boolean expire(String key, Duration timeout) {
            expiredKey = key;
            expiry = timeout;
            return true;
        }

        @Override
        public Boolean delete(String key) {
            deletedKey = key;
            values.remove(key);
            return true;
        }
    }
}
