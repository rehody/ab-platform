package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.report.model.UniqueMetricReport;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CachedUniqueMetricReport(
        CachedExperimentMetricReportMeta meta,
        int totalParticipants,
        int participantsWithMetricEvent,
        BigDecimal conversionRate,
        List<CachedUniqueVariantSummary> variants) {

    public CachedUniqueMetricReport {
        variants = List.copyOf(variants);
    }

    public static CachedUniqueMetricReport from(UniqueMetricReport uniqueMetricReport) {
        return new CachedUniqueMetricReport(
                CachedExperimentMetricReportMeta.from(uniqueMetricReport.meta()),
                uniqueMetricReport.totalParticipants(),
                uniqueMetricReport.participantsWithMetricEvent(),
                uniqueMetricReport.conversionRate(),
                uniqueMetricReport.variants().stream()
                        .map(CachedUniqueVariantSummary::from)
                        .toList());
    }

    public UniqueMetricReport toModel() {
        return new UniqueMetricReport(
                meta.toModel(),
                totalParticipants,
                participantsWithMetricEvent,
                conversionRate,
                variants.stream().map(CachedUniqueVariantSummary::toModel).toList());
    }

    public record CachedUniqueVariantSummary(
            UUID variantId,
            String key,
            int position,
            int participants,
            int participantsWithMetricEvent,
            BigDecimal conversionRate) {

        public static CachedUniqueVariantSummary from(UniqueMetricReport.UniqueVariantSummary uniqueVariantSummary) {
            return new CachedUniqueVariantSummary(
                    uniqueVariantSummary.variantId(),
                    uniqueVariantSummary.key(),
                    uniqueVariantSummary.position(),
                    uniqueVariantSummary.participants(),
                    uniqueVariantSummary.participantsWithMetricEvent(),
                    uniqueVariantSummary.conversionRate());
        }

        public UniqueMetricReport.UniqueVariantSummary toModel() {
            return new UniqueMetricReport.UniqueVariantSummary(
                    variantId, key, position, participants, participantsWithMetricEvent, conversionRate);
        }
    }
}
