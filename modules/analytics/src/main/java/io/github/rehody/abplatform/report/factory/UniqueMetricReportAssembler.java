package io.github.rehody.abplatform.report.factory;

import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.report.dto.response.UniqueMetricReportResponse;
import io.github.rehody.abplatform.report.dto.response.UniqueMetricReportResponse.UniqueVariantSummary;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.repository.aggregate.UniqueMetricVariantAggregate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UniqueMetricReportAssembler {

    private static final int RATIO_SCALE = 4;

    private final ExperimentReportMetaFactory experimentReportMetaFactory;

    public UniqueMetricReportResponse assemble(
            Experiment experiment,
            MetricDefinition metricDefinition,
            List<ExperimentVariant> orderedVariants,
            Map<UUID, Integer> participantsByVariant,
            Map<UUID, UniqueMetricVariantAggregate> metricAggregatesByVariant,
            ExperimentReportWindow reportWindow) {

        List<UniqueVariantSummary> variants = orderedVariants.stream()
                .map(variant -> toVariantSummary(variant, participantsByVariant, metricAggregatesByVariant))
                .toList();

        int totalParticipants =
                variants.stream().mapToInt(UniqueVariantSummary::participants).sum();

        int participantsWithMetricEvent = variants.stream()
                .mapToInt(UniqueVariantSummary::participantsWithMetricEvent)
                .sum();

        return new UniqueMetricReportResponse(
                experimentReportMetaFactory.create(experiment, metricDefinition, reportWindow),
                totalParticipants,
                participantsWithMetricEvent,
                calculateRatio(participantsWithMetricEvent, totalParticipants),
                variants);
    }

    private UniqueVariantSummary toVariantSummary(
            ExperimentVariant variant,
            Map<UUID, Integer> participantsByVariant,
            Map<UUID, UniqueMetricVariantAggregate> metricAggregatesByVariant) {
        int participants = participantsByVariant.getOrDefault(variant.id(), 0);
        UniqueMetricVariantAggregate metricAggregate = metricAggregatesByVariant.get(variant.id());
        int participantsWithMetricEvent = resolveParticipantsWithMetricEvent(metricAggregate);

        return new UniqueVariantSummary(
                variant.id(),
                variant.key(),
                variant.position(),
                participants,
                participantsWithMetricEvent,
                calculateRatio(participantsWithMetricEvent, participants));
    }

    private int resolveParticipantsWithMetricEvent(UniqueMetricVariantAggregate metricAggregate) {
        if (metricAggregate == null) {
            return 0;
        }

        return metricAggregate.participantsWithMetricEvent();
    }

    private BigDecimal calculateRatio(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
