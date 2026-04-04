package io.github.rehody.abplatform.metric.model;

import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import java.math.BigDecimal;
import java.util.UUID;

public record MetricDefinition(
        UUID id,
        String key,
        String name,
        MetricType type,
        MetricDirection direction,
        MetricSeverity severity,
        BigDecimal deviationThreshold) {

    public boolean isCountable() {
        return type == MetricType.COUNTABLE;
    }
}
