package io.github.rehody.abplatform.evaluation.model;

import io.github.rehody.abplatform.evaluation.enums.ExperimentMetricRiskStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExperimentMetricRisk(
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

    public boolean isOpen() {
        return status == ExperimentMetricRiskStatus.OPEN;
    }

    public boolean isResolved() {
        return status == ExperimentMetricRiskStatus.RESOLVED;
    }
}
