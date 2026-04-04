package io.github.rehody.abplatform.evaluation.dto.response;

import io.github.rehody.abplatform.evaluation.enums.ExperimentMetricRiskStatus;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricRisk;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExperimentMetricRiskResponse(
        UUID id,
        UUID experimentId,
        String metricKey,
        UUID variantId,
        ExperimentMetricRiskStatus status,
        Instant openedAt,
        Instant resolvedAt,
        String resolutionComment,
        Instant lastEvaluatedAt,
        BigDecimal lastBadDeviation,
        BigDecimal worstBadDeviation,
        Instant autoPausedAt) {

    public static ExperimentMetricRiskResponse from(ExperimentMetricRisk risk) {
        if (risk == null) {
            return null;
        }

        return new ExperimentMetricRiskResponse(
                risk.id(),
                risk.experimentId(),
                risk.metricKey(),
                risk.variantId(),
                risk.status(),
                risk.openedAt(),
                risk.resolvedAt(),
                risk.resolutionComment(),
                risk.lastEvaluatedAt(),
                risk.lastBadDeviation(),
                risk.worstBadDeviation(),
                risk.autoPausedAt());
    }
}
