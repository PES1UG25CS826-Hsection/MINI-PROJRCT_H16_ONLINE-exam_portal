package edu.pes.exam.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private static final Duration IDLE_TTL = Duration.ofMinutes(30);
    private static final String PREFIX = "portal:session:";
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public TokenService(StringRedisTemplate redis) { this.redis = redis; }

    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(PREFIX + token, userId.toString(), IDLE_TTL);
        return token;
    }

    public String userId(String token) {
        String key = PREFIX + token;
        String id = redis.opsForValue().get(key);
        if (id != null) redis.expire(key, IDLE_TTL);
        return id;
    }

    public void revoke(String token) { redis.delete(PREFIX + token); }
}
