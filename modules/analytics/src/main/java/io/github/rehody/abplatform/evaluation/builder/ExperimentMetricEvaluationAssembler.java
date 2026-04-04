package io.github.rehody.abplatform.evaluation.builder;

import io.github.rehody.abplatform.config.AnalyticsEvaluationProperties;
import io.github.rehody.abplatform.evaluation.enums.MetricComparisonStatus;
import io.github.rehody.abplatform.evaluation.enums.TrafficStatus;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.TrafficEvaluation;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.VariantComparison;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.VariantMetricAggregate;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.VariantTrafficShare;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.repository.aggregate.CountableMetricVariantAggregate;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentMetricEvaluationAssembler {

    private static final int RATIO_SCALE = 4;

    private final ExperimentMetricEvaluationMetaFactory experimentMetricEvaluationMetaFactory;
    private final AnalyticsEvaluationProperties analyticsEvaluationProperties;

    public ExperimentMetricEvaluationReport assemble(
            Experiment experiment,
            MetricDefinition metricDefinition,
            List<ExperimentVariant> orderedVariants,
            Map<UUID, Integer> participantsByVariant,
            Map<UUID, CountableMetricVariantAggregate> metricAggregatesByVariant,
            Map<UUID, ExperimentMetricRisk> risksByVariant,
            ExperimentReportWindow reportWindow) {

        List<VariantMetricAggregate> variants = orderedVariants.stream()
                .map(variant -> toVariantMetricAggregate(variant, participantsByVariant, metricAggregatesByVariant))
                .toList();
        Map<UUID, VariantMetricAggregate> aggregatesByVariantId = mapAggregatesByVariantId(variants);
        TrafficEvaluation traffic = buildTrafficEvaluation(orderedVariants, aggregatesByVariantId);
        VariantMetricAggregate controlAggregate = findControlAggregate(orderedVariants, aggregatesByVariantId);
        List<VariantComparison> comparisons = buildComparisons(
                orderedVariants, aggregatesByVariantId, controlAggregate, metricDefinition, risksByVariant);

        return new ExperimentMetricEvaluationReport(
                experimentMetricEvaluationMetaFactory.create(experiment, metricDefinition, reportWindow),
                traffic,
                variants,
                comparisons);
    }

    private VariantMetricAggregate toVariantMetricAggregate(
            ExperimentVariant variant,
            Map<UUID, Integer> participantsByVariant,
            Map<UUID, CountableMetricVariantAggregate> metricAggregatesByVariant) {
        int participants = participantsByVariant.getOrDefault(variant.id(), 0);
        CountableMetricVariantAggregate metricAggregate = metricAggregatesByVariant.get(variant.id());
        int totalMetricEvents = resolveTotalMetricEvents(metricAggregate);

        return new VariantMetricAggregate(
                variant.id(),
                variant.key(),
                variant.position(),
                participants,
                totalMetricEvents,
                calculateRatio(totalMetricEvents, participants));
    }

    private Map<UUID, VariantMetricAggregate> mapAggregatesByVariantId(List<VariantMetricAggregate> variants) {
        return variants.stream().collect(Collectors.toMap(VariantMetricAggregate::variantId, Function.identity()));
    }

    private TrafficEvaluation buildTrafficEvaluation(
            List<ExperimentVariant> orderedVariants, Map<UUID, VariantMetricAggregate> aggregatesByVariantId) {
        int totalParticipants = aggregatesByVariantId.values().stream()
                .mapToInt(VariantMetricAggregate::participants)
                .sum();

        BigDecimal totalWeight =
                orderedVariants.stream().map(ExperimentVariant::weight).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<VariantTrafficShare> trafficShares = orderedVariants.stream()
                .map(variant -> toVariantTrafficShare(variant, aggregatesByVariantId, totalParticipants, totalWeight))
                .toList();

        TrafficStatus trafficStatus = TrafficStatus.NORMAL;
        if (totalParticipants < analyticsEvaluationProperties.getMinimumParticipantsForTrafficWarning()) {
            trafficStatus = TrafficStatus.INSUFFICIENT_DATA;
        } else if (hasTrafficWarning(trafficShares)) {
            trafficStatus = TrafficStatus.WARNING;
        }

        return new TrafficEvaluation(trafficStatus, totalParticipants, List.copyOf(trafficShares));
    }

    private VariantMetricAggregate findControlAggregate(
            List<ExperimentVariant> orderedVariants, Map<UUID, VariantMetricAggregate> aggregatesByVariantId) {
        UUID controlVariantId = orderedVariants.stream()
                .filter(ExperimentVariant::isControl)
                .map(ExperimentVariant::id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Control variant not found"));

        VariantMetricAggregate controlAggregate = aggregatesByVariantId.get(controlVariantId);
        if (controlAggregate == null) {
            throw new IllegalStateException("Control variant aggregate not found");
        }

        return controlAggregate;
    }

    private List<VariantComparison> buildComparisons(
            List<ExperimentVariant> orderedVariants,
            Map<UUID, VariantMetricAggregate> aggregatesByVariantId,
            VariantMetricAggregate controlAggregate,
            MetricDefinition metricDefinition,
            Map<UUID, ExperimentMetricRisk> risksByVariant) {
        return orderedVariants.stream()
                .filter(ExperimentVariant::isRegular)
                .map(variant -> toVariantComparison(
                        variant, aggregatesByVariantId, controlAggregate, metricDefinition, risksByVariant))
                .toList();
    }

    private VariantTrafficShare toVariantTrafficShare(
            ExperimentVariant variant,
            Map<UUID, VariantMetricAggregate> aggregatesByVariantId,
            int totalParticipants,
            BigDecimal totalWeight) {
        VariantMetricAggregate aggregate = aggregatesByVariantId.get(variant.id());
        BigDecimal expectedShare = divide(variant.weight(), totalWeight);
        BigDecimal actualShare =
                divide(BigDecimal.valueOf(aggregate.participants()), BigDecimal.valueOf(totalParticipants));

        BigDecimal shareDelta = expectedShare.subtract(actualShare).abs();

        return new VariantTrafficShare(
                variant.id(),
                variant.key(),
                variant.position(),
                aggregate.participants(),
                expectedShare,
                actualShare,
                shareDelta);
    }

    private VariantComparison toVariantComparison(
            ExperimentVariant variant,
            Map<UUID, VariantMetricAggregate> aggregatesByVariantId,
            VariantMetricAggregate controlAggregate,
            MetricDefinition metricDefinition,
            Map<UUID, ExperimentMetricRisk> risksByVariant) {

        VariantMetricAggregate aggregate = aggregatesByVariantId.get(variant.id());
        boolean sufficientData = hasSufficientData(controlAggregate, aggregate);
        boolean zeroControl = controlAggregate.eventsPerParticipant().compareTo(BigDecimal.ZERO) == 0;

        BigDecimal absoluteDifference =
                aggregate.eventsPerParticipant().subtract(controlAggregate.eventsPerParticipant());

        BigDecimal relativeDeviation = calculateRelativeDeviation(
                controlAggregate.eventsPerParticipant(), aggregate.eventsPerParticipant(), zeroControl);

        return new VariantComparison(
                variant.id(),
                variant.key(),
                variant.position(),
                determineComparisonStatus(
                        metricDefinition.direction(),
                        metricDefinition.deviationThreshold(),
                        sufficientData,
                        relativeDeviation),
                sufficientData,
                zeroControl,
                controlAggregate.eventsPerParticipant(),
                aggregate.eventsPerParticipant(),
                absoluteDifference,
                relativeDeviation,
                risksByVariant.get(variant.id()));
    }

    private boolean hasTrafficWarning(List<VariantTrafficShare> trafficShares) {
        return trafficShares.stream()
                .anyMatch(share ->
                        share.shareDelta().compareTo(analyticsEvaluationProperties.getTrafficShareWarningThreshold())
                                > 0);
    }

    private boolean hasSufficientData(VariantMetricAggregate controlAggregate, VariantMetricAggregate aggregate) {
        return controlAggregate.totalMetricEvents() >= analyticsEvaluationProperties.getMinimumEventsForAnalysis()
                && aggregate.totalMetricEvents() >= analyticsEvaluationProperties.getMinimumEventsForAnalysis();
    }

    private MetricComparisonStatus determineComparisonStatus(
            MetricDirection metricDirection,
            BigDecimal deviationThreshold,
            boolean sufficientData,
            BigDecimal relativeDeviation) {
        if (!sufficientData) {
            return MetricComparisonStatus.INSUFFICIENT_DATA;
        }

        boolean hasNegativeDeviation = false;
        if (metricDirection == MetricDirection.MORE_IS_BETTER) {
            hasNegativeDeviation = relativeDeviation.compareTo(deviationThreshold.negate()) < 0;
        } else if (metricDirection == MetricDirection.LESS_IS_BETTER) {
            hasNegativeDeviation = relativeDeviation.compareTo(deviationThreshold) > 0;
        }

        if (hasNegativeDeviation) {
            return MetricComparisonStatus.NEGATIVE_DEVIATION;
        }

        return MetricComparisonStatus.NORMAL;
    }

    private int resolveTotalMetricEvents(CountableMetricVariantAggregate metricAggregate) {
        if (metricAggregate == null) {
            return 0;
        }

        return metricAggregate.totalMetricEvents();
    }

    private BigDecimal calculateRelativeDeviation(
            BigDecimal controlRate, BigDecimal standardRate, boolean zeroControl) {
        BigDecimal denominator = controlRate;
        if (zeroControl) {
            denominator = BigDecimal.ONE;
        }

        return standardRate.subtract(controlRate).divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRatio(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
