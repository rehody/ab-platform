package io.github.rehody.abplatform.evaluation.factory;

import io.github.rehody.abplatform.evaluation.enums.ExperimentMetricRiskStatus;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.VariantComparison;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExperimentMetricRiskFactory {

    public ExperimentMetricRisk createResolved(ExperimentMetricRisk currentRisk, String comment, Instant resolvedAt) {
        return new ExperimentMetricRisk(
                currentRisk.id(),
                currentRisk.experimentId(),
                currentRisk.metricKey(),
                currentRisk.variantId(),
                ExperimentMetricRiskStatus.RESOLVED,
                currentRisk.openedAt(),
                resolvedAt,
                comment,
                currentRisk.lastEvaluatedAt(),
                currentRisk.lastBadDeviation(),
                currentRisk.worstBadDeviation(),
                currentRisk.autoPausedAt());
    }

    public ExperimentMetricRisk createOpen(
            Experiment experiment,
            MetricDefinition metricDefinition,
            VariantComparison comparison,
            BigDecimal badDeviation,
            Instant openedAt) {
        return new ExperimentMetricRisk(
                UUID.randomUUID(),
                experiment.id(),
                metricDefinition.key(),
                comparison.variantId(),
                ExperimentMetricRiskStatus.OPEN,
                openedAt,
                null,
                null,
                openedAt,
                badDeviation,
                badDeviation,
                null);
    }

    public ExperimentMetricRisk reopen(ExperimentMetricRisk currentRisk, BigDecimal badDeviation, Instant reopenedAt) {
        return new ExperimentMetricRisk(
                currentRisk.id(),
                currentRisk.experimentId(),
                currentRisk.metricKey(),
                currentRisk.variantId(),
                ExperimentMetricRiskStatus.OPEN,
                reopenedAt,
                null,
                null,
                reopenedAt,
                badDeviation,
                badDeviation,
                null);
    }

    public ExperimentMetricRisk refreshOpenRisk(
            ExperimentMetricRisk currentRisk,
            BigDecimal badDeviation,
            BigDecimal worstBadDeviation,
            Instant autoPausedAt,
            Instant evaluatedAt) {
        return new ExperimentMetricRisk(
                currentRisk.id(),
                currentRisk.experimentId(),
                currentRisk.metricKey(),
                currentRisk.variantId(),
                currentRisk.status(),
                currentRisk.openedAt(),
                currentRisk.resolvedAt(),
                currentRisk.resolutionComment(),
                evaluatedAt,
                badDeviation,
                worstBadDeviation,
                autoPausedAt);
    }
}
