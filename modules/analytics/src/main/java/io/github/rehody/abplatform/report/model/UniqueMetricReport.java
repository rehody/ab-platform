package io.github.rehody.abplatform.report.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UniqueMetricReport(
        ExperimentMetricReportMeta meta,
        int totalParticipants,
        int participantsWithMetricEvent,
        BigDecimal conversionRate,
        List<UniqueVariantSummary> variants)
        implements ExperimentMetricReport {

    public record UniqueVariantSummary(
            UUID variantId,
            String key,
            int position,
            int participants,
            int participantsWithMetricEvent,
            BigDecimal conversionRate) {}
}
