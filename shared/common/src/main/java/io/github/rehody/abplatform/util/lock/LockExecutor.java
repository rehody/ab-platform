package io.github.rehody.abplatform.util.lock;

import java.time.Duration;
import java.util.function.Supplier;

public interface LockExecutor {

    <T> T withLock(LockNamespace namespace, String key, Supplier<T> action);

    <T> T withLock(LockNamespace namespace, String key, Duration waitTime, Duration leaseTime, Supplier<T> action);
}
