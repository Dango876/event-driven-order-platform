package com.procurehub.notification.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

@Service
public class NotificationRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(NotificationRateLimiter.class);

    private static final String LEAKY_BUCKET_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local leakPerSecond = tonumber(ARGV[3])
            local ttlSeconds = tonumber(ARGV[4])

            local values = redis.call('HMGET', key, 'level', 'last')
            local level = tonumber(values[1]) or 0
            local last = tonumber(values[2]) or now

            if now > last then
              local leaked = math.floor((now - last) * leakPerSecond / 1000)
              if leaked > 0 then
                level = level - leaked
                if level < 0 then
                  level = 0
                end
              end
            end

            if (level + 1) > capacity then
              redis.call('HMSET', key, 'level', tostring(level), 'last', tostring(now))
              redis.call('EXPIRE', key, ttlSeconds)
              return 0
            end

            level = level + 1
            redis.call('HMSET', key, 'level', tostring(level), 'last', tostring(now))
            redis.call('EXPIRE', key, ttlSeconds)
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final NotificationRateLimitProperties properties;
    private final DefaultRedisScript<Long> allowScript;

    public NotificationRateLimiter(StringRedisTemplate redisTemplate, NotificationRateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.allowScript = new DefaultRedisScript<>();
        this.allowScript.setScriptText(LEAKY_BUCKET_LUA);
        this.allowScript.setResultType(Long.class);
    }

    public boolean allowForUser(long userId) {
        return allowForBucket(properties.getUserBucketKeyPrefix() + userId);
    }

    public boolean allowForOrderFallback(long orderId) {
        return allowForBucket(properties.getOrderBucketKeyPrefix() + orderId);
    }

    public void rememberOrderUser(long orderId, long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    properties.getOrderUserKeyPrefix() + orderId,
                    String.valueOf(userId),
                    Duration.ofSeconds(properties.getOrderUserTtlSeconds())
            );
        } catch (Exception ex) {
            // Fail-open: notification flow should continue even if Redis is unavailable.
            log.warn(
                    "Failed to store order->user mapping (orderId={}, userId={}), continue without mapping",
                    orderId,
                    userId,
                    ex
            );
        }
    }

    public Long findUserIdByOrder(long orderId) {
        if (!properties.isEnabled()) {
            return null;
        }
        try {
            String value = redisTemplate.opsForValue().get(properties.getOrderUserKeyPrefix() + orderId);
            if (value == null || value.isBlank()) {
                return null;
            }
            return Long.parseLong(value);
        } catch (Exception ex) {
            // Fail-open: no mapping available if Redis is down.
            log.warn("Failed to read order->user mapping for orderId={}, continue with fallback", orderId, ex);
            return null;
        }
    }

    private boolean allowForBucket(String bucketKey) {
        if (!properties.isEnabled()) {
            return true;
        }

        try {
            long now = Instant.now().toEpochMilli();
            Long allowed = redisTemplate.execute(
                    allowScript,
                    Collections.singletonList(bucketKey),
                    String.valueOf(now),
                    String.valueOf(properties.getCapacity()),
                    String.valueOf(properties.getLeakPerSecond()),
                    String.valueOf(properties.getBucketKeyTtlSeconds())
            );
            return Long.valueOf(1L).equals(allowed);
        } catch (Exception ex) {
            // Fail-open: avoid dropping notifications due temporary Redis issues.
            log.warn("Rate limiter unavailable for bucket={}, allow by fail-open", bucketKey, ex);
            return true;
        }
    }
}
