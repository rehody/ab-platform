package io.github.rehody.abplatform.report.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UniqueMetricExperimentReportResponse(
        ExperimentMetricReportMeta meta,

        int totalParticipants,
        int totalConverters,

        BigDecimal conversionRate,

        List<UniqueVariantSummary> variants)
        implements ExperimentReportResponse {

    public record UniqueVariantSummary(
            UUID variantId, String key, int position, int participants, int converters, BigDecimal conversionRate) {}
}
