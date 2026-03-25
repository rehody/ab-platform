package io.github.rehody.abplatform.util.cache;

import java.time.Duration;

public record RedisCacheConfig(
        Duration valueTtl, Duration missTtl, double ttlSpread, String redisKeyPrefix, String invalidationTopic) {

    public RedisCacheConfig {
        if (valueTtl == null) {
            throw new IllegalArgumentException("valueTtl is required");
        }
        if (missTtl == null) {
            throw new IllegalArgumentException("missTtl is required");
        }
        if (invalidationTopic == null || invalidationTopic.isBlank()) {
            throw new IllegalArgumentException("invalidationTopic is required");
        }
    }
}
