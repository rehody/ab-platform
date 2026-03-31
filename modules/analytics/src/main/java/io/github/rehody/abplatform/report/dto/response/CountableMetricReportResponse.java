package io.github.rehody.abplatform.report.dto.response;

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

    public record CountableVariantSummary(
            UUID variantId,
            String key,
            int position,
            int participants,
            int participantsWithMetricEvent,
            int totalMetricEvents,
            BigDecimal participantConversionRate,
            BigDecimal eventsPerParticipant) {}
}
