package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.report.model.CountableMetricReport;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CachedCountableMetricReport(
        CachedExperimentMetricReportMeta meta,
        int totalParticipants,
        int participantsWithMetricEvent,
        int totalMetricEvents,
        BigDecimal participantConversionRate,
        BigDecimal eventsPerParticipant,
        List<CachedCountableVariantSummary> variants) {

    public CachedCountableMetricReport {
        variants = List.copyOf(variants);
    }

    public static CachedCountableMetricReport from(CountableMetricReport countableMetricReport) {
        return new CachedCountableMetricReport(
                CachedExperimentMetricReportMeta.from(countableMetricReport.meta()),
                countableMetricReport.totalParticipants(),
                countableMetricReport.participantsWithMetricEvent(),
                countableMetricReport.totalMetricEvents(),
                countableMetricReport.participantConversionRate(),
                countableMetricReport.eventsPerParticipant(),
                countableMetricReport.variants().stream()
                        .map(CachedCountableVariantSummary::from)
                        .toList());
    }

    public CountableMetricReport toModel() {
        return new CountableMetricReport(
                meta.toModel(),
                totalParticipants,
                participantsWithMetricEvent,
                totalMetricEvents,
                participantConversionRate,
                eventsPerParticipant,
                variants.stream().map(CachedCountableVariantSummary::toModel).toList());
    }

    public record CachedCountableVariantSummary(
            UUID variantId,
            String key,
            int position,
            int participants,
            int participantsWithMetricEvent,
            int totalMetricEvents,
            BigDecimal participantConversionRate,
            BigDecimal eventsPerParticipant) {

        public static CachedCountableVariantSummary from(
                CountableMetricReport.CountableVariantSummary countableVariantSummary) {
            return new CachedCountableVariantSummary(
                    countableVariantSummary.variantId(),
                    countableVariantSummary.key(),
                    countableVariantSummary.position(),
                    countableVariantSummary.participants(),
                    countableVariantSummary.participantsWithMetricEvent(),
                    countableVariantSummary.totalMetricEvents(),
                    countableVariantSummary.participantConversionRate(),
                    countableVariantSummary.eventsPerParticipant());
        }

        public CountableMetricReport.CountableVariantSummary toModel() {
            return new CountableMetricReport.CountableVariantSummary(
                    variantId,
                    key,
                    position,
                    participants,
                    participantsWithMetricEvent,
                    totalMetricEvents,
                    participantConversionRate,
                    eventsPerParticipant);
        }
    }
}
