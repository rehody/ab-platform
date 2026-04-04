package io.github.rehody.abplatform.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.evaluation.enums.ExperimentMetricRiskStatus;
import io.github.rehody.abplatform.evaluation.enums.MetricComparisonStatus;
import io.github.rehody.abplatform.evaluation.enums.TrafficStatus;
import io.github.rehody.abplatform.evaluation.factory.ExperimentMetricRiskFactory;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.TrafficEvaluation;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.VariantComparison;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.evaluation.policy.ExperimentMetricRiskPolicy;
import io.github.rehody.abplatform.evaluation.repository.ExperimentMetricRiskRepository;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentMetricRiskServiceTest {

    @Mock
    private ExperimentMetricRiskRepository experimentMetricRiskRepository;

    @Mock
    private ExperimentMetricAutoPauseService experimentMetricAutoPauseService;

    private ExperimentMetricRiskService experimentMetricRiskService;

    @BeforeEach
    void setUp() {
        experimentMetricRiskService = new ExperimentMetricRiskService(
                experimentMetricRiskRepository,
                new ExperimentMetricRiskPolicy(),
                new ExperimentMetricRiskFactory(),
                experimentMetricAutoPauseService);
    }

    @Test
    void applyEvaluation_shouldNotCreateRiskWhenTrafficIsInsufficient() {
        ExperimentMetricEvaluationReport report =
                report(TrafficStatus.INSUFFICIENT_DATA, comparison(MetricComparisonStatus.NEGATIVE_DEVIATION, null));

        experimentMetricRiskService.applyEvaluation(runningExperiment(), metricDefinition(), report);

        verify(experimentMetricRiskRepository, never()).save(any());
        verify(experimentMetricRiskRepository, never()).update(any());
    }

    @Test
    void applyEvaluation_shouldNotReopenResolvedRiskWhenTrafficIsInsufficient() {
        ExperimentMetricEvaluationReport report = report(
                TrafficStatus.INSUFFICIENT_DATA,
                comparison(MetricComparisonStatus.NEGATIVE_DEVIATION, resolvedRisk(null)));

        experimentMetricRiskService.applyEvaluation(runningExperiment(), metricDefinition(), report);

        verify(experimentMetricRiskRepository, never()).save(any());
        verify(experimentMetricRiskRepository, never()).update(any());
    }

    @Test
    void applyEvaluation_shouldNotUpdateOpenRiskWhenTrafficIsInsufficient() {
        ExperimentMetricEvaluationReport report = report(
                TrafficStatus.INSUFFICIENT_DATA,
                comparison(MetricComparisonStatus.NEGATIVE_DEVIATION, openRisk(null, new BigDecimal("0.10"))));

        experimentMetricRiskService.applyEvaluation(runningExperiment(), metricDefinition(), report);

        verify(experimentMetricRiskRepository, never()).save(any());
        verify(experimentMetricRiskRepository, never()).update(any());
        verify(experimentMetricAutoPauseService, never()).pause(any(), any());
    }

    @Test
    void applyEvaluation_shouldKeepOpenRiskWhenDeviationIsNoLongerNegative() {
        ExperimentMetricRisk currentRisk = openRisk(null, new BigDecimal("0.10"));
        ExperimentMetricEvaluationReport report =
                report(TrafficStatus.NORMAL, comparison(MetricComparisonStatus.NORMAL, currentRisk));

        experimentMetricRiskService.applyEvaluation(runningExperiment(), metricDefinition(), report);

        verify(experimentMetricRiskRepository, never()).save(any());
        verify(experimentMetricRiskRepository, never()).update(any());
        verify(experimentMetricAutoPauseService, never()).pause(any(), any());
    }

    @Test
    void applyEvaluation_shouldNotAutoPauseAgainWhenRiskWasAlreadyAutoPaused() {
        Instant autoPausedAt = Instant.parse("2026-04-04T10:15:30Z");
        ExperimentMetricRisk currentRisk = openRisk(autoPausedAt, new BigDecimal("0.10"));
        ExperimentMetricEvaluationReport report =
                report(TrafficStatus.NORMAL, comparison(MetricComparisonStatus.NEGATIVE_DEVIATION, currentRisk));

        experimentMetricRiskService.applyEvaluation(runningExperiment(), metricDefinition(), report);

        verify(experimentMetricAutoPauseService, never()).pause(any(), any());

        ArgumentCaptor<ExperimentMetricRisk> riskCaptor = ArgumentCaptor.forClass(ExperimentMetricRisk.class);
        verify(experimentMetricRiskRepository).update(riskCaptor.capture());
        ExperimentMetricRisk updatedRisk = riskCaptor.getValue();

        assertThat(updatedRisk.status()).isEqualTo(ExperimentMetricRiskStatus.OPEN);
        assertThat(updatedRisk.autoPausedAt()).isEqualTo(autoPausedAt);
        assertThat(updatedRisk.lastBadDeviation()).isEqualByComparingTo("0.20");
        assertThat(updatedRisk.worstBadDeviation()).isEqualByComparingTo("0.20");
    }

    private Experiment runningExperiment() {
        return new Experiment(
                UUID.randomUUID(), "checkout-redesign", List.of(), ExperimentState.RUNNING, 7L, null, null);
    }

    private MetricDefinition metricDefinition() {
        return new MetricDefinition(
                UUID.randomUUID(),
                "orders",
                "Orders",
                MetricType.COUNTABLE,
                MetricDirection.MORE_IS_BETTER,
                MetricSeverity.HIGH,
                new BigDecimal("0.10"));
    }

    private ExperimentMetricEvaluationReport report(TrafficStatus trafficStatus, VariantComparison comparison) {
        return new ExperimentMetricEvaluationReport(
                null, new TrafficEvaluation(trafficStatus, 100, List.of()), List.of(), List.of(comparison));
    }

    private VariantComparison comparison(MetricComparisonStatus status, ExperimentMetricRisk risk) {
        BigDecimal relativeDeviation =
                status == MetricComparisonStatus.NEGATIVE_DEVIATION ? new BigDecimal("-0.20") : BigDecimal.ZERO;

        return new VariantComparison(
                UUID.randomUUID(),
                "treatment",
                1,
                status,
                true,
                false,
                new BigDecimal("1.00"),
                new BigDecimal("0.80"),
                new BigDecimal("-0.20"),
                relativeDeviation,
                risk);
    }

    private ExperimentMetricRisk resolvedRisk(Instant autoPausedAt) {
        Instant openedAt = Instant.parse("2026-04-03T10:15:30Z");
        return new ExperimentMetricRisk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "orders",
                UUID.randomUUID(),
                ExperimentMetricRiskStatus.RESOLVED,
                openedAt,
                openedAt.plusSeconds(3600),
                "manual",
                openedAt.plusSeconds(3600),
                new BigDecimal("0.15"),
                new BigDecimal("0.15"),
                autoPausedAt);
    }

    private ExperimentMetricRisk openRisk(Instant autoPausedAt, BigDecimal worstBadDeviation) {
        Instant openedAt = Instant.parse("2026-04-03T10:15:30Z");
        return new ExperimentMetricRisk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "orders",
                UUID.randomUUID(),
                ExperimentMetricRiskStatus.OPEN,
                openedAt,
                null,
                null,
                openedAt,
                worstBadDeviation,
                worstBadDeviation,
                autoPausedAt);
    }
}
