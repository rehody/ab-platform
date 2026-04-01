package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.report.model.ExperimentMetricReport;
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
public class ExperimentMetricReportCache {

    private final TwoLevelCache<CachedExperimentMetricReport> cache;

    public ExperimentMetricReportCache(
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            ExperimentMetricReportCacheProperties properties) {
        this.cache = TwoLevelCacheFactory.create(
                redissonClient, objectMapper, properties, CachedExperimentMetricReport.class);
    }

    @PostConstruct
    public void subscribeInvalidationTopic() {
        cache.start();
    }

    @PreDestroy
    public void unsubscribeInvalidationTopic() {
        cache.stop();
    }

    public Optional<ExperimentMetricReport> getOrLoad(String key, Supplier<Optional<ExperimentMetricReport>> loader) {
        return cache.getOrLoad(key, () -> loader.get().map(CachedExperimentMetricReport::from))
                .map(CachedExperimentMetricReport::toModel);
    }
}
