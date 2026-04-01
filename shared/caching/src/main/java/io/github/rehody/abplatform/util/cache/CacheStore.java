package io.github.rehody.abplatform.util.cache;

import java.util.Optional;
import java.util.function.Consumer;

public interface CacheStore<T> {

    Optional<T> readValue(String key);

    boolean hasMiss(String key);

    void writeValue(String key, T value);

    void writeMiss(String key);

    void invalidate(String key);

    void publishInvalidation(String key);

    int subscribeInvalidation(Consumer<String> listener);

    void unsubscribeInvalidation(int listenerId);
}
