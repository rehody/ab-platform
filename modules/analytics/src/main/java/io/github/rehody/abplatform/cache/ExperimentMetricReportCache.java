package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.util.cache.CacheCodec;
import io.github.rehody.abplatform.util.cache.LocalCacheConfig;
import io.github.rehody.abplatform.util.cache.ObjectMapperCacheCodec;
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
public class ExperimentMetricReportCache {

    private final TwoLevelCache<CachedExperimentMetricReport> cache;

    public ExperimentMetricReportCache(
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            ExperimentMetricReportCacheProperties properties) {

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

        CacheCodec<CachedExperimentMetricReport> codec =
                ObjectMapperCacheCodec.forClass(objectMapper, CachedExperimentMetricReport.class);

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

    public Optional<CachedExperimentMetricReport> getOrLoad(
            String key, Supplier<Optional<CachedExperimentMetricReport>> loader) {
        return cache.getOrLoad(key, loader);
    }
}
