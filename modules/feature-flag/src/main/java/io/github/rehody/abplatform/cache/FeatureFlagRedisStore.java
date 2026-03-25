package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class FeatureFlagRedisStore {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagRedisStore.class);
    private static final String DEFAULT_REDIS_KEY_PREFIX = "ab-platform:cache:feature-flag:v1:";
    private static final String VALUE_KEY_PREFIX = "value:";
    private static final String MISS_KEY_PREFIX = "miss:";
    private static final String MISS_MARKER = "1";
    private static final long MIN_TTL_MILLIS = 1_000L;

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final Duration valueTtl;
    private final Duration missTtl;
    private final String redisKeyPrefix;
    private final double ttlSpread;
    private final RTopic invalidationTopic;

    public FeatureFlagRedisStore(
            RedissonClient redissonClient, ObjectMapper objectMapper, FeatureFlagCacheProperties properties) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.valueTtl = properties.getL2ValueTtl();
        this.missTtl = properties.getL2MissTtl();
        this.redisKeyPrefix = normalizePrefix(properties.getRedisKeyPrefix());
        this.ttlSpread = normalizeSpread(properties.getTtlSpread());
        this.invalidationTopic = redissonClient.getTopic(properties.getInvalidationTopic());
    }

    public Optional<FeatureFlagResponse> readValue(String key) {
        String serialized =
                executeIgnoreFailure("read value", () -> valueBucket(key).get());

        if (serialized == null || serialized.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(serialized, FeatureFlagResponse.class));
        } catch (Exception ex) {
            log.warn("Failed to deserialize feature flag cache value for key '{}'", key, ex);
            executeIgnoreFailure("remove broken value", () -> {
                valueBucket(key).delete();
                return null;
            });
            return Optional.empty();
        }
    }

    public boolean hasMiss(String key) {
        return MISS_MARKER.equals(
                executeIgnoreFailure("read miss marker", () -> missBucket(key).get()));
    }

    public void writeValue(String key, FeatureFlagResponse value) {
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

    public void writeMiss(String key) {
        executeIgnoreFailure("write miss marker", () -> {
            valueBucket(key).delete();
            missBucket(key).set(MISS_MARKER, spreadTtl(missTtl));
            return null;
        });
    }

    public void invalidate(String key) {
        executeIgnoreFailure("invalidate", () -> {
            valueBucket(key).delete();
            missBucket(key).delete();
            return null;
        });
    }

    public void publishInvalidation(String key) {
        executeIgnoreFailure("publish invalidation", () -> {
            invalidationTopic.publish(key);
            return null;
        });
    }

    private RBucket<String> valueBucket(String key) {
        return redissonClient.getBucket(redisKeyPrefix + VALUE_KEY_PREFIX + key);
    }

    private RBucket<String> missBucket(String key) {
        return redissonClient.getBucket(redisKeyPrefix + MISS_KEY_PREFIX + key);
    }

    private String serialize(FeatureFlagResponse value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Failed to serialize feature flag cache value", ex);
            return null;
        }
    }

    private Duration spreadTtl(Duration ttl) {
        long baseMillis = ttl.toMillis();
        if (baseMillis <= MIN_TTL_MILLIS) {
            return Duration.ofMillis(MIN_TTL_MILLIS);
        }

        double randomPart = ThreadLocalRandom.current().nextDouble(-ttlSpread, ttlSpread);
        long adjustedMillis = baseMillis + Math.round(baseMillis * randomPart);

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

    private <T> T executeIgnoreFailure(String operationName, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RuntimeException ex) {
            log.warn("Redis cache operation failed: {}", operationName, ex);
            return null;
        }
    }
}
