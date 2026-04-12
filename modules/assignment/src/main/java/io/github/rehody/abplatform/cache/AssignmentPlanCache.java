package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshot;
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
public class AssignmentPlanCache {

    private final TwoLevelCache<VariantAllocationSnapshot> cache;

    public AssignmentPlanCache(
            RedissonClient redissonClient, ObjectMapper objectMapper, AssignmentPlanCacheProperties properties) {
        this.cache =
                TwoLevelCacheFactory.create(redissonClient, objectMapper, properties, VariantAllocationSnapshot.class);
    }

    @PostConstruct
    public void subscribeInvalidationTopic() {
        cache.start();
    }

    @PreDestroy
    public void unsubscribeInvalidationTopic() {
        cache.stop();
    }

    public Optional<VariantAllocationSnapshot> getOrLoad(
            String key, Supplier<Optional<VariantAllocationSnapshot>> loader) {
        return cache.getOrLoad(key, loader);
    }
}
