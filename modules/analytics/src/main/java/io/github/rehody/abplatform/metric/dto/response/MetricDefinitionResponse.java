package io.github.rehody.abplatform.metric.dto.response;

import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import java.math.BigDecimal;
import java.util.UUID;

public record MetricDefinitionResponse(
        UUID id,
        String key,
        String name,
        MetricType type,
        MetricDirection direction,
        MetricSeverity severity,
        BigDecimal deviationThreshold) {

    public static MetricDefinitionResponse from(MetricDefinition metricDefinition) {
        return new MetricDefinitionResponse(
                metricDefinition.id(),
                metricDefinition.key(),
                metricDefinition.name(),
                metricDefinition.type(),
                metricDefinition.direction(),
                metricDefinition.severity(),
                metricDefinition.deviationThreshold());
    }
}
