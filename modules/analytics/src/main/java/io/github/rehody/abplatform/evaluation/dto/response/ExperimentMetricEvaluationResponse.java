package io.github.rehody.abplatform.evaluation.dto.response;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.evaluation.enums.MetricComparisonStatus;
import io.github.rehody.abplatform.evaluation.enums.TrafficStatus;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationMeta;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.VariantComparison;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExperimentMetricEvaluationResponse(
        ExperimentMetricEvaluationMetaResponse meta,
        TrafficEvaluationResponse traffic,
        List<VariantMetricAggregateResponse> variants,
        List<VariantComparisonResponse> comparisons) {

    public static ExperimentMetricEvaluationResponse from(ExperimentMetricEvaluationReport report) {
        return new ExperimentMetricEvaluationResponse(
                ExperimentMetricEvaluationMetaResponse.from(report.meta()),
                TrafficEvaluationResponse.from(report.traffic()),
                report.variants().stream()
                        .map(VariantMetricAggregateResponse::from)
                        .toList(),
                report.comparisons().stream()
                        .map(VariantComparisonResponse::from)
                        .toList());
    }

    public record ExperimentMetricEvaluationMetaResponse(
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
            Instant trackedTo) {

        private static ExperimentMetricEvaluationMetaResponse from(ExperimentMetricEvaluationMeta meta) {
            return new ExperimentMetricEvaluationMetaResponse(
                    meta.experimentId(),
                    meta.flagKey(),
                    meta.metricKey(),
                    meta.metricDirection(),
                    meta.metricSeverity(),
                    meta.deviationThreshold(),
                    meta.experimentState(),
                    meta.experimentStartedAt(),
                    meta.experimentCompletedAt(),
                    meta.trackedFrom(),
                    meta.trackedTo());
        }
    }

    public record TrafficEvaluationResponse(
            TrafficStatus status, int totalParticipants, List<VariantTrafficShareResponse> variants) {

        private static TrafficEvaluationResponse from(ExperimentMetricEvaluationReport.TrafficEvaluation traffic) {
            return new TrafficEvaluationResponse(
                    traffic.status(),
                    traffic.totalParticipants(),
                    traffic.variants().stream()
                            .map(VariantTrafficShareResponse::from)
                            .toList());
        }
    }

    public record VariantTrafficShareResponse(
            UUID variantId,
            String key,
            int position,
            int participants,
            BigDecimal expectedShare,
            BigDecimal actualShare,
            BigDecimal shareDelta) {

        private static VariantTrafficShareResponse from(ExperimentMetricEvaluationReport.VariantTrafficShare share) {
            return new VariantTrafficShareResponse(
                    share.variantId(),
                    share.key(),
                    share.position(),
                    share.participants(),
                    share.expectedShare(),
                    share.actualShare(),
                    share.shareDelta());
        }
    }

    public record VariantMetricAggregateResponse(
            UUID variantId,
            String key,
            int position,
            int participants,
            int totalMetricEvents,
            BigDecimal eventsPerParticipant) {

        private static VariantMetricAggregateResponse from(
                ExperimentMetricEvaluationReport.VariantMetricAggregate aggregate) {
            return new VariantMetricAggregateResponse(
                    aggregate.variantId(),
                    aggregate.key(),
                    aggregate.position(),
                    aggregate.participants(),
                    aggregate.totalMetricEvents(),
                    aggregate.eventsPerParticipant());
        }
    }

    public record VariantComparisonResponse(
            UUID variantId,
            String key,
            int position,
            MetricComparisonStatus status,
            boolean sufficientData,
            boolean zeroControl,
            BigDecimal controlEventsPerParticipant,
            BigDecimal standardEventsPerParticipant,
            BigDecimal absoluteDifference,
            BigDecimal relativeDeviation,
            ExperimentMetricRiskResponse risk) {

        private static VariantComparisonResponse from(VariantComparison comparison) {
            return new VariantComparisonResponse(
                    comparison.variantId(),
                    comparison.key(),
                    comparison.position(),
                    comparison.status(),
                    comparison.sufficientData(),
                    comparison.zeroControl(),
                    comparison.controlEventsPerParticipant(),
                    comparison.standardEventsPerParticipant(),
                    comparison.absoluteDifference(),
                    comparison.relativeDeviation(),
                    ExperimentMetricRiskResponse.from(comparison.risk()));
        }
    }
}
