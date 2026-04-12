package io.github.rehody.abplatform.evaluation.model;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExperimentMetricEvaluationMeta(
        UUID experimentId,
        String flagKey,
        String metricKey,
        MetricDirection metricDirection,
        MetricSeverity metricSeverity,
        BigDecimal deviationThreshold,
        ExperimentState experimentState,
        Instant experimentStartedAt,
        Instant experimentCompletedAt,
        Instant trackedFrom,
        Instant trackedTo) {}
