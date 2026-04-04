package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.metric.model.MetricDefinition;
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
public class MetricDefinitionCache {

    private final TwoLevelCache<CachedMetricDefinition> cache;

    public MetricDefinitionCache(
            RedissonClient redissonClient, ObjectMapper objectMapper, MetricDefinitionCacheProperties properties) {
        this.cache =
                TwoLevelCacheFactory.create(redissonClient, objectMapper, properties, CachedMetricDefinition.class);
    }

    @PostConstruct
    public void subscribeInvalidationTopic() {
        cache.start();
    }

    @PreDestroy
    public void unsubscribeInvalidationTopic() {
        cache.stop();
    }

    public Optional<MetricDefinition> getOrLoad(String key, Supplier<Optional<MetricDefinition>> loader) {
        return cache.getOrLoad(key, () -> loader.get().map(CachedMetricDefinition::from))
                .map(CachedMetricDefinition::toModel);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }
}
