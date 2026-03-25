package io.github.rehody.abplatform.util.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

@Slf4j
public class RedisCacheStore<T> implements CacheStore<T> {

    private static final String DEFAULT_REDIS_KEY_PREFIX = "ab-platform:cache:";
    private static final String VALUE_KEY_PREFIX = "value:";
    private static final String MISS_KEY_PREFIX = "miss:";
    private static final String MISS_MARKER = "1";
    private static final long MIN_TTL_MILLIS = 1_000L;

    private final RedissonClient redissonClient;
    private final CacheCodec<T> codec;
    private final Duration valueTtl;
    private final Duration missTtl;
    private final String redisKeyPrefix;
    private final double ttlSpread;
    private final String invalidationTopic;

    public RedisCacheStore(RedissonClient redissonClient, CacheCodec<T> codec, RedisCacheConfig config) {
        this.redissonClient = redissonClient;
        this.codec = codec;
        this.valueTtl = config.valueTtl();
        this.missTtl = config.missTtl();
        this.redisKeyPrefix = normalizePrefix(config.redisKeyPrefix());
        this.ttlSpread = normalizeSpread(config.ttlSpread());
        this.invalidationTopic = config.invalidationTopic().trim();
    }

    @Override
    public Optional<T> readValue(String key) {
        String serialized =
                executeIgnoreFailure("read value", () -> valueBucket(key).get());

        if (serialized == null || serialized.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(codec.read(serialized));
        } catch (Exception ex) {
            log.warn("Failed to deserialize cache value for key '{}'", key, ex);
            executeIgnoreFailure("remove broken value", () -> {
                valueBucket(key).delete();
                return null;
            });
            return Optional.empty();
        }
    }

    @Override
    public boolean hasMiss(String key) {
        return MISS_MARKER.equals(
                executeIgnoreFailure("read miss marker", () -> missBucket(key).get()));
    }

    @Override
    public void writeValue(String key, T value) {
        String serialized = serialize(value);
        if (serialized == null) {
            return;
        }

        executeIgnoreFailure("write value", () -> {
            valueBucket(key).set(serialized, spreadTtl(valueTtl));
            missBucket(key).delete();
            return null;
        });
    }

    @Override
    public void writeMiss(String key) {
        executeIgnoreFailure("write miss marker", () -> {
            valueBucket(key).delete();
            missBucket(key).set(MISS_MARKER, spreadTtl(missTtl));
            return null;
        });
    }

    @Override
    public void invalidate(String key) {
        executeIgnoreFailure("invalidate", () -> {
            valueBucket(key).delete();
            missBucket(key).delete();
            return null;
        });
    }

    @Override
    public void publishInvalidation(String key) {
        executeIgnoreFailure("publish invalidation", () -> {
            topic().publish(key);
            return null;
        });
    }

    @Override
    public int subscribeInvalidation(Consumer<String> listener) {
        return topic().addListener(String.class, (_, key) -> listener.accept(key));
    }

    @Override
    public void unsubscribeInvalidation(int listenerId) {
        topic().removeListener(listenerId);
    }

    private RTopic topic() {
        return redissonClient.getTopic(invalidationTopic);
    }

    private RBucket<String> valueBucket(String key) {
        return redissonClient.getBucket(redisKeyPrefix + VALUE_KEY_PREFIX + key);
    }

    private RBucket<String> missBucket(String key) {
        return redissonClient.getBucket(redisKeyPrefix + MISS_KEY_PREFIX + key);
    }

    private String serialize(T value) {
        try {
            return codec.write(value);
        } catch (Exception ex) {
            log.warn("Failed to serialize cache value", ex);
            return null;
        }
    }

    private Duration spreadTtl(Duration ttl) {
        long baseMillis = ttl.toMillis();
        if (baseMillis <= MIN_TTL_MILLIS) {
            return Duration.ofMillis(MIN_TTL_MILLIS);
        }

        double spreadPart = ThreadLocalRandom.current().nextDouble(-ttlSpread, ttlSpread);
        long adjustedMillis = baseMillis + Math.round(baseMillis * spreadPart);
        return Duration.ofMillis(Math.max(MIN_TTL_MILLIS, adjustedMillis));
    }

    private String normalizePrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim();
        if (normalized.isEmpty()) {
            return DEFAULT_REDIS_KEY_PREFIX;
        }
        return normalized.endsWith(":") ? normalized : normalized + ":";
    }

    private double normalizeSpread(double value) {
        if (value < 0.0d) {
            return 0.0d;
        }
        return Math.min(value, 1.0d);
    }

    private <R> R executeIgnoreFailure(String operationName, Supplier<R> operation) {
        try {
            return operation.get();
        } catch (RuntimeException ex) {
            log.warn("Redis cache operation failed: {}", operationName, ex);
            return null;
        }
    }
}
