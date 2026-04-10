package io.github.rehody.abplatform.rollout.policy;

import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.rollout.enums.ExperimentRolloutDecision;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExperimentRolloutPolicy {

    public ExperimentRolloutDecision decide(
            Experiment experiment, List<ExperimentMetricEvaluationReport> evaluationReports) {
        if (evaluationReports.isEmpty()) {
            return ExperimentRolloutDecision.HOLD;
        }

        if (hasAbnormalTraffic(evaluationReports)) {
            return ExperimentRolloutDecision.HOLD;
        }

        if (hasInsufficientData(evaluationReports)) {
            return ExperimentRolloutDecision.HOLD;
        }

        if (hasNegativeDeviation(evaluationReports)) {
            if (experiment.rolloutPlan().afterRollback()) {
                return ExperimentRolloutDecision.PAUSE;
            }

            return ExperimentRolloutDecision.ROLLBACK;
        }

        return ExperimentRolloutDecision.ADVANCE;
    }

    private boolean hasAbnormalTraffic(List<ExperimentMetricEvaluationReport> evaluationReports) {
        return evaluationReports.stream().anyMatch(report -> !report.traffic().isNormal());
    }

    private boolean hasInsufficientData(List<ExperimentMetricEvaluationReport> evaluationReports) {
        return evaluationReports.stream()
                .flatMap(report -> report.comparisons().stream())
                .anyMatch(comparison -> !comparison.sufficientData());
    }

    private boolean hasNegativeDeviation(List<ExperimentMetricEvaluationReport> evaluationReports) {
        return evaluationReports.stream()
                .flatMap(report -> report.comparisons().stream())
                .anyMatch(ExperimentMetricEvaluationReport.VariantComparison::hasNegativeDeviation);
    }
}
