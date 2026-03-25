package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.util.cache.CacheCodec;
import io.github.rehody.abplatform.util.cache.LocalCacheConfig;
import io.github.rehody.abplatform.util.cache.RedisCacheConfig;
import io.github.rehody.abplatform.util.cache.RedisCacheStore;
import io.github.rehody.abplatform.util.cache.TwoLevelCache;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.function.Supplier;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class FeatureFlagCache {

    private final TwoLevelCache<FeatureFlagResponse> cache;

    public FeatureFlagCache(
            RedissonClient redissonClient, ObjectMapper objectMapper, FeatureFlagCacheProperties properties) {

        LocalCacheConfig localConfig = new LocalCacheConfig(
                properties.getL1ValueTtl(),
                properties.getL1MissTtl(),
                properties.getL1ValueSize(),
                properties.getL1MissSize());

        RedisCacheConfig redisConfig = new RedisCacheConfig(
                properties.getL2ValueTtl(),
                properties.getL2MissTtl(),
                properties.getTtlSpread(),
                properties.getRedisKeyPrefix(),
                properties.getInvalidationTopic());

        CacheCodec<FeatureFlagResponse> codec = new FeatureFlagResponseCacheCodec(objectMapper);

        this.cache = new TwoLevelCache<>(new RedisCacheStore<>(redissonClient, codec, redisConfig), localConfig);
    }

    @PostConstruct
    public void subscribeInvalidationTopic() {
        cache.start();
    }

    @PreDestroy
    public void unsubscribeInvalidationTopic() {
        cache.stop();
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }

    public Optional<FeatureFlagResponse> getOrLoad(String key, Supplier<Optional<FeatureFlagResponse>> loader) {
        return cache.getOrLoad(key, loader);
    }
}
