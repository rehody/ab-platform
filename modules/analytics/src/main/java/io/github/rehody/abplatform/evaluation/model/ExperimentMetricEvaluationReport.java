package io.github.rehody.abplatform.evaluation.model;

import io.github.rehody.abplatform.evaluation.enums.MetricComparisonStatus;
import io.github.rehody.abplatform.evaluation.enums.TrafficStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ExperimentMetricEvaluationReport(
        ExperimentMetricEvaluationMeta meta,
        TrafficEvaluation traffic,
        List<VariantMetricAggregate> variants,
        List<VariantComparison> comparisons) {

    public record TrafficEvaluation(TrafficStatus status, int totalParticipants, List<VariantTrafficShare> variants) {

        public boolean isNormal() {
            return status == TrafficStatus.NORMAL;
        }
    }

    public record VariantTrafficShare(
            UUID variantId,
            String key,
            int position,
            int participants,
            BigDecimal expectedShare,
            BigDecimal actualShare,
            BigDecimal shareDelta) {}

    public record VariantMetricAggregate(
            UUID variantId,
            String key,
            int position,
            int participants,
            int totalMetricEvents,
            BigDecimal eventsPerParticipant) {}

    public record VariantComparison(
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
            ExperimentMetricRisk risk) {

        public boolean hasNegativeDeviation() {
            return status == MetricComparisonStatus.NEGATIVE_DEVIATION;
        }
    }
}
