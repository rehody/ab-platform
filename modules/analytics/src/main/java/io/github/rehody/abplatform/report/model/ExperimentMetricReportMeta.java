package io.github.rehody.abplatform.report.model;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.metric.enums.MetricType;
import java.time.Instant;
import java.util.UUID;

public record ExperimentMetricReportMeta(
        UUID experimentId,
        String flagKey,
        String metricKey,
        MetricType metricType,
        ExperimentState experimentState,
        Instant experimentStartedAt,
        Instant experimentCompletedAt,
        Instant trackedFrom,
        Instant trackedTo) {}
