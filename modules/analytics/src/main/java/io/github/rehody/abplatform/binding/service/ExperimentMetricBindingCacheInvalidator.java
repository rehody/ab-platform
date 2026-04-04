package io.github.rehody.abplatform.binding.service;

import io.github.rehody.abplatform.cache.ExperimentMetricReportCache;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentMetricBindingCacheInvalidator {

    private final ExperimentMetricReportCache experimentMetricReportCache;

    public void invalidateReports(UUID experimentId, List<String> metricKeys) {
        metricKeys.forEach(metricKey -> experimentMetricReportCache.invalidate(cacheKey(experimentId, metricKey)));
    }

    private String cacheKey(UUID experimentId, String metricKey) {
        return "%s:metric:%s".formatted(experimentId, metricKey);
    }
}
