package io.github.rehody.abplatform.util.cache;

import java.time.Duration;

public record LocalCacheConfig(Duration valueTtl, Duration missTtl, long valueSize, long missSize) {

    public LocalCacheConfig {
        if (valueTtl == null) {
            throw new IllegalArgumentException("valueTtl is required");
        }
        if (missTtl == null) {
            throw new IllegalArgumentException("missTtl is required");
        }
        if (valueSize <= 0L) {
            throw new IllegalArgumentException("valueSize must be positive");
        }
        if (missSize <= 0L) {
            throw new IllegalArgumentException("missSize must be positive");
        }
    }
}
