package io.github.rehody.abplatform.report.factory;

import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.report.model.CountableMetricReport;
import io.github.rehody.abplatform.report.model.CountableMetricReport.CountableVariantSummary;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.repository.aggregate.CountableMetricVariantAggregate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CountableMetricReportAssembler {

    private static final int RATIO_SCALE = 4;

    private final ExperimentReportMetaFactory experimentReportMetaFactory;

    public CountableMetricReport assemble(
            Experiment experiment,
            MetricDefinition metricDefinition,
            List<ExperimentVariant> orderedVariants,
            Map<UUID, Integer> participantsByVariant,
            Map<UUID, CountableMetricVariantAggregate> metricAggregatesByVariant,
            ExperimentReportWindow reportWindow) {

        List<CountableVariantSummary> variants = orderedVariants.stream()
                .map(variant -> toVariantSummary(variant, participantsByVariant, metricAggregatesByVariant))
                .toList();

        int totalParticipants = variants.stream()
                .mapToInt(CountableVariantSummary::participants)
                .sum();

        int participantsWithMetricEvent = variants.stream()
                .mapToInt(CountableVariantSummary::participantsWithMetricEvent)
                .sum();

        int totalMetricEvents = variants.stream()
                .mapToInt(CountableVariantSummary::totalMetricEvents)
                .sum();

        return new CountableMetricReport(
                experimentReportMetaFactory.create(experiment, metricDefinition, reportWindow),
                totalParticipants,
                participantsWithMetricEvent,
                totalMetricEvents,
                calculateRatio(participantsWithMetricEvent, totalParticipants),
                calculateRatio(totalMetricEvents, totalParticipants),
                variants);
    }

    private CountableVariantSummary toVariantSummary(
            ExperimentVariant variant,
            Map<UUID, Integer> participantsByVariant,
            Map<UUID, CountableMetricVariantAggregate> metricAggregatesByVariant) {
        int participants = participantsByVariant.getOrDefault(variant.id(), 0);
        CountableMetricVariantAggregate metricAggregate = metricAggregatesByVariant.get(variant.id());
        int participantsWithMetricEvent = resolveParticipantsWithMetricEvent(metricAggregate);
        int totalMetricEvents = resolveTotalMetricEvents(metricAggregate);

        return new CountableVariantSummary(
                variant.id(),
                variant.key(),
                variant.position(),
                participants,
                participantsWithMetricEvent,
                totalMetricEvents,
                calculateRatio(participantsWithMetricEvent, participants),
                calculateRatio(totalMetricEvents, participants));
    }

    private int resolveParticipantsWithMetricEvent(CountableMetricVariantAggregate metricAggregate) {
        if (metricAggregate == null) {
            return 0;
        }

        return metricAggregate.participantsWithMetricEvent();
    }

    private int resolveTotalMetricEvents(CountableMetricVariantAggregate metricAggregate) {
        if (metricAggregate == null) {
            return 0;
        }

        return metricAggregate.totalMetricEvents();
    }

    private BigDecimal calculateRatio(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
