package io.github.rehody.abplatform.rollout.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.evaluation.enums.MetricComparisonStatus;
import io.github.rehody.abplatform.evaluation.enums.TrafficStatus;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.TrafficEvaluation;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.VariantComparison;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import io.github.rehody.abplatform.rollout.enums.ExperimentRolloutDecision;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentRolloutPolicyTest {

    private final ExperimentRolloutPolicy experimentRolloutPolicy = new ExperimentRolloutPolicy();

    @Test
    void decide_shouldHoldWhenReportsAreMissing() {
        Experiment experiment = experiment(ExperimentRolloutPlan.initial());

        ExperimentRolloutDecision response = experimentRolloutPolicy.decide(experiment, List.of());

        assertThat(response).isEqualTo(ExperimentRolloutDecision.HOLD);
    }

    @Test
    void decide_shouldHoldWhenTrafficIsAbnormal() {
        Experiment experiment = experiment(ExperimentRolloutPlan.initial());

        ExperimentRolloutDecision response =
                experimentRolloutPolicy.decide(experiment, List.of(report(TrafficStatus.WARNING, List.of())));

        assertThat(response).isEqualTo(ExperimentRolloutDecision.HOLD);
    }

    @Test
    void decide_shouldHoldWhenDataIsInsufficient() {
        Experiment experiment = experiment(ExperimentRolloutPlan.initial());
        VariantComparison comparison = comparison(MetricComparisonStatus.INSUFFICIENT_DATA, false);

        ExperimentRolloutDecision response =
                experimentRolloutPolicy.decide(experiment, List.of(report(TrafficStatus.NORMAL, List.of(comparison))));

        assertThat(response).isEqualTo(ExperimentRolloutDecision.HOLD);
    }

    @Test
    void decide_shouldRollbackWhenNegativeDeviationAppearsBeforeRollback() {
        Experiment experiment = experiment(ExperimentRolloutPlan.initial());
        VariantComparison comparison = comparison(MetricComparisonStatus.NEGATIVE_DEVIATION, true);

        ExperimentRolloutDecision response =
                experimentRolloutPolicy.decide(experiment, List.of(report(TrafficStatus.NORMAL, List.of(comparison))));

        assertThat(response).isEqualTo(ExperimentRolloutDecision.ROLLBACK);
    }

    @Test
    void decide_shouldPauseWhenNegativeDeviationAppearsAfterRollback() {
        Experiment experiment = experiment(ExperimentRolloutPlan.of(5, true, false));
        VariantComparison comparison = comparison(MetricComparisonStatus.NEGATIVE_DEVIATION, true);

        ExperimentRolloutDecision response =
                experimentRolloutPolicy.decide(experiment, List.of(report(TrafficStatus.NORMAL, List.of(comparison))));

        assertThat(response).isEqualTo(ExperimentRolloutDecision.PAUSE);
    }

    @Test
    void decide_shouldHoldWhenExperimentCannotAdvance() {
        Experiment experiment = experiment(ExperimentRolloutPlan.of(100, false, false));
        VariantComparison comparison = comparison(MetricComparisonStatus.NORMAL, true);

        ExperimentRolloutDecision response =
                experimentRolloutPolicy.decide(experiment, List.of(report(TrafficStatus.NORMAL, List.of(comparison))));

        assertThat(response).isEqualTo(ExperimentRolloutDecision.HOLD);
    }

    @Test
    void decide_shouldAdvanceWhenTrafficAndMetricsAreHealthy() {
        Experiment experiment = experiment(ExperimentRolloutPlan.initial());
        VariantComparison comparison = comparison(MetricComparisonStatus.NORMAL, true);

        ExperimentRolloutDecision response =
                experimentRolloutPolicy.decide(experiment, List.of(report(TrafficStatus.NORMAL, List.of(comparison))));

        assertThat(response).isEqualTo(ExperimentRolloutDecision.ADVANCE);
    }

    private Experiment experiment(ExperimentRolloutPlan rolloutPlan) {
        return new Experiment(
                UUID.randomUUID(),
                "flag-orders",
                "CHECKOUT",
                rolloutPlan,
                List.of(),
                ExperimentState.RUNNING,
                4L,
                null,
                null);
    }

    private ExperimentMetricEvaluationReport report(TrafficStatus trafficStatus, List<VariantComparison> comparisons) {
        return new ExperimentMetricEvaluationReport(
                null, new TrafficEvaluation(trafficStatus, 100, List.of()), List.of(), comparisons);
    }

    private VariantComparison comparison(MetricComparisonStatus status, boolean sufficientData) {
        BigDecimal controlRate = new BigDecimal("1.00");
        BigDecimal standardRate =
                status == MetricComparisonStatus.NEGATIVE_DEVIATION ? new BigDecimal("0.80") : new BigDecimal("1.20");

        return new VariantComparison(
                UUID.randomUUID(),
                "treatment",
                1,
                status,
                sufficientData,
                false,
                controlRate,
                standardRate,
                standardRate.subtract(controlRate),
                standardRate.subtract(controlRate).divide(controlRate),
                null);
    }
}
