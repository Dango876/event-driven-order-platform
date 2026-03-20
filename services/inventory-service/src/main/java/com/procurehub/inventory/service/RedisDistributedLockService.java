package com.procurehub.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class RedisDistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLockService.class);

    private static final String RELEASE_LOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            else
              return 0
            end
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> releaseScript;

    @Value("${inventory.lock.enabled:true}")
    private boolean enabled;

    @Value("${inventory.lock.fail-open:true}")
    private boolean failOpen;

    @Value("${inventory.lock.wait-timeout-ms:1200}")
    private long waitTimeoutMs;

    @Value("${inventory.lock.lease-timeout-ms:5000}")
    private long leaseTimeoutMs;

    @Value("${inventory.lock.retry-delay-ms:50}")
    private long retryDelayMs;

    @Value("${inventory.lock.key-prefix:inventory:redlock:product:}")
    private String keyPrefix;

    public RedisDistributedLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.releaseScript = new DefaultRedisScript<>();
        this.releaseScript.setScriptText(RELEASE_LOCK_SCRIPT);
        this.releaseScript.setResultType(Long.class);
    }

    public <T> T withProductLock(Long productId, Supplier<T> action) {
        if (!enabled) {
            return action.get();
        }

        String lockKey = keyPrefix + productId;
        String lockToken = UUID.randomUUID().toString();

        boolean acquired;
        try {
            acquired = tryAcquire(lockKey, lockToken);
        } catch (Exception ex) {
            if (failOpen) {
                log.warn("Redis lock check failed (fail-open=true), productId={}, using DB lock fallback", productId, ex);
                return action.get();
            }
            throw new DistributedLockException("Redis lock acquisition failed for productId=" + productId, ex);
        }

        if (!acquired) {
            if (failOpen) {
                log.warn("Could not acquire Redis lock (fail-open=true), productId={}, using DB lock fallback", productId);
                return action.get();
            }
            throw new DistributedLockException("Could not acquire distributed lock for productId=" + productId);
        }

        try {
            return action.get();
        } finally {
            safelyRelease(lockKey, lockToken);
        }
    }

    private boolean tryAcquire(String lockKey, String lockToken) {
        long waitMs = Math.max(waitTimeoutMs, 0L);
        long retryMs = Math.max(retryDelayMs, 1L);
        long leaseMs = Math.max(leaseTimeoutMs, 1000L);

        long deadline = System.currentTimeMillis() + waitMs;

        do {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockToken, Duration.ofMillis(leaseMs));
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }

            if (System.currentTimeMillis() >= deadline) {
                break;
            }

            try {
                Thread.sleep(retryMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new DistributedLockException("Interrupted while waiting for distributed lock: " + lockKey, ie);
            }
        } while (true);

        return false;
    }

    private void safelyRelease(String lockKey, String lockToken) {
        try {
            redisTemplate.execute(releaseScript, Collections.singletonList(lockKey), lockToken);
        } catch (Exception ex) {
            log.warn("Failed to release Redis lock key={}", lockKey, ex);
        }
    }
}
