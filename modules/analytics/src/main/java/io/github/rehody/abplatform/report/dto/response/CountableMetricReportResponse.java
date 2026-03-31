package io.github.rehody.abplatform.report.dto.response;

import io.github.rehody.abplatform.report.model.CountableMetricReport;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CountableMetricReportResponse(
        ExperimentMetricReportMeta meta,

        int totalParticipants,
        int participantsWithMetricEvent,
        int totalMetricEvents,

        BigDecimal participantConversionRate,
        BigDecimal eventsPerParticipant,

        List<CountableVariantSummary> variants)
        implements ExperimentMetricReportResponse {

    public static CountableMetricReportResponse from(CountableMetricReport countableMetricReport) {
        return new CountableMetricReportResponse(
                ExperimentMetricReportMeta.from(countableMetricReport.meta()),
                countableMetricReport.totalParticipants(),
                countableMetricReport.participantsWithMetricEvent(),
                countableMetricReport.totalMetricEvents(),
                countableMetricReport.participantConversionRate(),
                countableMetricReport.eventsPerParticipant(),
                countableMetricReport.variants().stream()
                        .map(CountableVariantSummary::from)
                        .toList());
    }

    public record CountableVariantSummary(
            UUID variantId,
            String key,
            int position,
            int participants,
            int participantsWithMetricEvent,
            int totalMetricEvents,
            BigDecimal participantConversionRate,
            BigDecimal eventsPerParticipant) {

        public static CountableVariantSummary from(CountableMetricReport.CountableVariantSummary variantSummary) {
            return new CountableVariantSummary(
                    variantSummary.variantId(),
                    variantSummary.key(),
                    variantSummary.position(),
                    variantSummary.participants(),
                    variantSummary.participantsWithMetricEvent(),
                    variantSummary.totalMetricEvents(),
                    variantSummary.participantConversionRate(),
                    variantSummary.eventsPerParticipant());
        }
    }
}
