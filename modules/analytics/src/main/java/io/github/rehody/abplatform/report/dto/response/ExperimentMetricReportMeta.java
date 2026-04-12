package io.github.rehody.abplatform.report.dto.response;

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
        Instant trackedTo) {

    public static ExperimentMetricReportMeta from(
            io.github.rehody.abplatform.report.model.ExperimentMetricReportMeta experimentMetricReportMeta) {
        return new ExperimentMetricReportMeta(
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
}
