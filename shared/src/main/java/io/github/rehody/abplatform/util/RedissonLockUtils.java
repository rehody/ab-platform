package io.github.rehody.abplatform.util;

import java.util.function.Supplier;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public final class RedissonLockUtils {

    private RedissonLockUtils() {}

    public static <T> T withLock(RedissonClient client, String lockKey, Supplier<T> action) {
        RLock lock = client.getLock(lockKey);
        lock.lock();
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
