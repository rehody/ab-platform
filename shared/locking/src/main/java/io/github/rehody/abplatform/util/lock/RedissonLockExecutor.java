package io.github.rehody.abplatform.util.lock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedissonLockExecutor implements LockExecutor {

    private final RedissonClient redissonClient;

    @Value("${ab.lock.key-prefix:ab-platform:lock:}")
    private String keyPrefix;

    @Value("${ab.lock.wait-time:3s}")
    private Duration defaultWaitTime;

    @Value("${ab.lock.lease-time:10s}")
    private Duration defaultLeaseTime;

    @Override
    public <T> T withLock(LockNamespace namespace, String key, Supplier<T> action) {
        return withLock(namespace, key, defaultWaitTime, defaultLeaseTime, action);
    }

    @Override
    public <T> T withLock(
            LockNamespace namespace, String key, Duration waitTime, Duration leaseTime, Supplier<T> action) {
        String lockKey = composeLockKey(namespace, key);
        RLock lock = redissonClient.getLock(lockKey);

        boolean obtained;
        try {
            obtained = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LockObtainingException("Lock interrupted for key '%s'".formatted(lockKey), ex);
        }

        if (!obtained) {
            throw new LockObtainingException("Lock timeout for key '%s'".formatted(lockKey));
        }

        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String composeLockKey(LockNamespace namespace, String key) {
        if (namespace == null) {
            throw new IllegalArgumentException("namespace is required");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
        return "%s%s:%s".formatted(normalizePrefix(keyPrefix), namespace.value(), key.trim());
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("keyPrefix is required");
        }
        String normalized = prefix.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("keyPrefix is required");
        }
        return normalized.endsWith(":") ? normalized : normalized + ":";
    }
}
