package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import java.util.UUID;

public record CachedMetricDefinition(UUID id, String key, String name, MetricType type) {

    public static CachedMetricDefinition from(MetricDefinition metricDefinition) {
        return new CachedMetricDefinition(
                metricDefinition.id(), metricDefinition.key(), metricDefinition.name(), metricDefinition.type());
    }

    public MetricDefinition toModel() {
        return new MetricDefinition(id, key, name, type);
    }
}
