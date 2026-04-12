package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.model.Experiment;
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
public class ExperimentCache {

    private final TwoLevelCache<CachedExperiment> cache;

    public ExperimentCache(
            RedissonClient redissonClient, ObjectMapper objectMapper, ExperimentCacheProperties properties) {
        this.cache = TwoLevelCacheFactory.create(redissonClient, objectMapper, properties, CachedExperiment.class);
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

    public Optional<Experiment> getOrLoad(String key, Supplier<Optional<Experiment>> loader) {
        return cache.getOrLoad(key, () -> loader.get().map(CachedExperiment::from))
                .map(CachedExperiment::toModel);
    }
}
