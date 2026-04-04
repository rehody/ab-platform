package io.github.rehody.abplatform.binding.service;

import io.github.rehody.abplatform.cache.ExperimentMetricReportCache;
import io.github.rehody.abplatform.cache.ExperimentMetricReportCacheKeyFactory;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentMetricBindingCacheInvalidator {

    private final ExperimentMetricReportCache experimentMetricReportCache;
    private final ExperimentMetricReportCacheKeyFactory experimentMetricReportCacheKeyFactory;

    public void invalidateReports(UUID experimentId, List<String> metricKeys) {
        metricKeys.forEach(metricKey -> experimentMetricReportCache.invalidate(
                experimentMetricReportCacheKeyFactory.forExperimentMetric(experimentId, metricKey)));
    }
}
