package io.github.rehody.abplatform.report.dto.response;

import io.github.rehody.abplatform.report.model.UniqueMetricReport;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UniqueMetricReportResponse(
        ExperimentMetricReportMeta meta,

        int totalParticipants,
        int participantsWithMetricEvent,

        BigDecimal conversionRate,

        List<UniqueVariantSummary> variants)
        implements ExperimentMetricReportResponse {

    public static UniqueMetricReportResponse from(UniqueMetricReport uniqueMetricReport) {
        return new UniqueMetricReportResponse(
                ExperimentMetricReportMeta.from(uniqueMetricReport.meta()),
                uniqueMetricReport.totalParticipants(),
                uniqueMetricReport.participantsWithMetricEvent(),
                uniqueMetricReport.conversionRate(),
                uniqueMetricReport.variants().stream()
                        .map(UniqueVariantSummary::from)
                        .toList());
    }

    public record UniqueVariantSummary(
            UUID variantId,
            String key,
            int position,
            int participants,
            int participantsWithMetricEvent,
            BigDecimal conversionRate) {

        public static UniqueVariantSummary from(UniqueMetricReport.UniqueVariantSummary variantSummary) {
            return new UniqueVariantSummary(
                    variantSummary.variantId(),
                    variantSummary.key(),
                    variantSummary.position(),
                    variantSummary.participants(),
                    variantSummary.participantsWithMetricEvent(),
                    variantSummary.conversionRate());
        }
    }
}
