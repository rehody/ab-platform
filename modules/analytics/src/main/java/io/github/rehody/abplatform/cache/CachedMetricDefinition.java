package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import java.math.BigDecimal;
import java.util.UUID;

public record CachedMetricDefinition(
        UUID id,
        String key,
        String name,
        MetricType type,
        MetricDirection direction,
        MetricSeverity severity,
        BigDecimal deviationThreshold) {

    public static CachedMetricDefinition from(MetricDefinition metricDefinition) {
        return new CachedMetricDefinition(
                metricDefinition.id(),
                metricDefinition.key(),
                metricDefinition.name(),
                metricDefinition.type(),
                metricDefinition.direction(),
                metricDefinition.severity(),
                metricDefinition.deviationThreshold());
    }

    public MetricDefinition toModel() {
        return new MetricDefinition(id, key, name, type, direction, severity, deviationThreshold);
    }
}
