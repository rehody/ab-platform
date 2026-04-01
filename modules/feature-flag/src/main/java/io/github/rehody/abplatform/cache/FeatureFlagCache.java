package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.util.cache.TwoLevelCache;
import io.github.rehody.abplatform.util.cache.TwoLevelCacheFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.function.Supplier;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class FeatureFlagCache {

    private final TwoLevelCache<CachedFeatureFlag> cache;

    public FeatureFlagCache(
            RedissonClient redissonClient, ObjectMapper objectMapper, FeatureFlagCacheProperties properties) {
        this.cache = TwoLevelCacheFactory.create(redissonClient, objectMapper, properties, CachedFeatureFlag.class);
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

    public Optional<FeatureFlag> getOrLoad(String key, Supplier<Optional<FeatureFlag>> loader) {
        return cache.getOrLoad(key, () -> loader.get().map(CachedFeatureFlag::from))
                .map(CachedFeatureFlag::toModel);
    }
}
