package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.report.model.ExperimentMetricReportMeta;
import java.time.Instant;
import java.util.UUID;

public record CachedExperimentMetricReportMeta(
        UUID experimentId,
        String flagKey,
        String metricKey,
        MetricType metricType,
        ExperimentState experimentState,
        Instant experimentStartedAt,
        Instant experimentCompletedAt,
        Instant trackedFrom,
        Instant trackedTo) {

    public static CachedExperimentMetricReportMeta from(ExperimentMetricReportMeta experimentMetricReportMeta) {
        return new CachedExperimentMetricReportMeta(
                experimentMetricReportMeta.experimentId(),
                experimentMetricReportMeta.flagKey(),
                experimentMetricReportMeta.metricKey(),
                experimentMetricReportMeta.metricType(),
                experimentMetricReportMeta.experimentState(),
                experimentMetricReportMeta.experimentStartedAt(),
                experimentMetricReportMeta.experimentCompletedAt(),
                experimentMetricReportMeta.trackedFrom(),
                experimentMetricReportMeta.trackedTo());
    }

    public ExperimentMetricReportMeta toModel() {
        return new ExperimentMetricReportMeta(
                experimentId,
                flagKey,
                metricKey,
                metricType,
                experimentState,
                experimentStartedAt,
                experimentCompletedAt,
                trackedFrom,
                trackedTo);
    }
}
