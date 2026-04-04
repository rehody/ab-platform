package io.github.rehody.abplatform.cache;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExperimentMetricReportCacheKeyFactory {

    public String forExperimentMetric(UUID experimentId, String metricKey) {
        return "%s:metric:%s".formatted(experimentId, metricKey);
    }
}
