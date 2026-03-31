package io.github.rehody.abplatform.report.dto.response;

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

    public record UniqueVariantSummary(
            UUID variantId,
            String key,
            int position,
            int participants,
            int participantsWithMetricEvent,
            BigDecimal conversionRate) {}
}
