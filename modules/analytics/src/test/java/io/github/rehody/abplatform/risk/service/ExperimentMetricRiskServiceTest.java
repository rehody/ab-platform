package io.github.rehody.abplatform.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.evaluation.enums.MetricComparisonStatus;
import io.github.rehody.abplatform.evaluation.enums.TrafficStatus;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.TrafficEvaluation;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.VariantComparison;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.risk.enums.ExperimentMetricRiskStatus;
import io.github.rehody.abplatform.risk.factory.ExperimentMetricRiskFactory;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.risk.policy.ExperimentMetricRiskPolicy;
import io.github.rehody.abplatform.risk.repository.ExperimentMetricRiskRepository;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
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

    @Mock
    private LockExecutor lockExecutor;

    private ExperimentMetricRiskService experimentMetricRiskService;

    @BeforeEach
    void setUp() {
        experimentMetricRiskService = new ExperimentMetricRiskService(
                experimentMetricRiskRepository,
                new ExperimentMetricRiskPolicy(),
                new ExperimentMetricRiskFactory(),
                experimentMetricAutoPauseService,
                lockExecutor);
        lenient()
                .when(lockExecutor.withLock(any(LockNamespace.class), any(String.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
    }

    @Test
    void shouldAcquireRiskLockAndReloadRiskWhenResolvingRisk() {
        UUID riskId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        ExperimentMetricRisk initialRisk = openRisk(riskId, experimentId, "orders", null, new BigDecimal("0.10"));
        ExperimentMetricRisk lockedRisk =
                openRisk(riskId, experimentId, "orders", Instant.parse("2026-04-04T08:00:00Z"), new BigDecimal("0.12"));
        AtomicBoolean lockHeld = new AtomicBoolean(false);

        when(lockExecutor.withLock(any(LockNamespace.class), any(String.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    lockHeld.set(true);
                    try {
                        return ((Supplier<?>) invocation.getArgument(2)).get();
                    } finally {
                        lockHeld.set(false);
                    }
                });
        when(experimentMetricRiskRepository.findById(riskId))
                .thenAnswer(invocation -> java.util.Optional.of(lockHeld.get() ? lockedRisk : initialRisk));

        ExperimentMetricRisk response = experimentMetricRiskService.resolve(riskId, "manual");

        ArgumentCaptor<LockNamespace> namespaceCaptor = ArgumentCaptor.forClass(LockNamespace.class);
        ArgumentCaptor<ExperimentMetricRisk> riskCaptor = ArgumentCaptor.forClass(ExperimentMetricRisk.class);
        verify(lockExecutor)
                .withLock(
                        namespaceCaptor.capture(), eq("%s:%s".formatted(experimentId, "orders")), any(Supplier.class));
        verify(experimentMetricRiskRepository, times(2)).findById(riskId);
        verify(experimentMetricRiskRepository).update(riskCaptor.capture());

        ExperimentMetricRisk resolvedRisk = riskCaptor.getValue();
        assertThat(namespaceCaptor.getValue().value()).isEqualTo("experiment-metric-risk");
        assertThat(resolvedRisk.status()).isEqualTo(ExperimentMetricRiskStatus.RESOLVED);
        assertThat(resolvedRisk.resolutionComment()).isEqualTo("manual");
        assertThat(resolvedRisk.autoPausedAt()).isEqualTo(lockedRisk.autoPausedAt());
        assertThat(response).isEqualTo(resolvedRisk);
    }

    @Test
    void shouldAcquireRiskLockWhenTrafficIsNotNormal() {
        Experiment experiment = runningExperiment();
        MetricDefinition metricDefinition = metricDefinition();
        ExperimentMetricEvaluationReport report =
                report(TrafficStatus.INSUFFICIENT_DATA, comparison(MetricComparisonStatus.NEGATIVE_DEVIATION, null));

        experimentMetricRiskService.applyEvaluation(experiment, metricDefinition, report);

        ArgumentCaptor<LockNamespace> namespaceCaptor = ArgumentCaptor.forClass(LockNamespace.class);
        verify(lockExecutor)
                .withLock(
                        namespaceCaptor.capture(),
                        eq("%s:%s".formatted(experiment.id(), metricDefinition.key())),
                        any(Supplier.class));
        assertThat(namespaceCaptor.getValue().value()).isEqualTo("experiment-metric-risk");
        verify(experimentMetricRiskRepository, never()).save(any());
        verify(experimentMetricRiskRepository, never()).update(any());
    }

    @Test
    void shouldCreateOpenRiskWhenNegativeDeviationHasNoCurrentRisk() {
        Experiment experiment = runningExperiment();
        MetricDefinition metricDefinition = metricDefinition();
        ExperimentMetricEvaluationReport report =
                report(TrafficStatus.NORMAL, comparison(MetricComparisonStatus.NEGATIVE_DEVIATION, null));

        experimentMetricRiskService.applyEvaluation(experiment, metricDefinition, report);

        ArgumentCaptor<ExperimentMetricRisk> riskCaptor = ArgumentCaptor.forClass(ExperimentMetricRisk.class);
        verify(experimentMetricRiskRepository).save(riskCaptor.capture());
        ExperimentMetricRisk savedRisk = riskCaptor.getValue();
        assertThat(savedRisk.status()).isEqualTo(ExperimentMetricRiskStatus.OPEN);
        assertThat(savedRisk.experimentId()).isEqualTo(experiment.id());
        assertThat(savedRisk.metricKey()).isEqualTo(metricDefinition.key());
    }

    @Test
    void shouldReopenResolvedRiskWhenNegativeDeviationReturns() {
        ExperimentMetricEvaluationReport report =
                report(TrafficStatus.NORMAL, comparison(MetricComparisonStatus.NEGATIVE_DEVIATION, resolvedRisk(null)));

        experimentMetricRiskService.applyEvaluation(runningExperiment(), metricDefinition(), report);

        ArgumentCaptor<ExperimentMetricRisk> riskCaptor = ArgumentCaptor.forClass(ExperimentMetricRisk.class);
        verify(experimentMetricRiskRepository).update(riskCaptor.capture());
        ExperimentMetricRisk reopenedRisk = riskCaptor.getValue();

        assertThat(reopenedRisk.status()).isEqualTo(ExperimentMetricRiskStatus.OPEN);
        assertThat(reopenedRisk.resolvedAt()).isNull();
        assertThat(reopenedRisk.resolutionComment()).isNull();
    }

    @Test
    void shouldSkipRiskUpdateWhenTrafficIsNotNormal() {
        ExperimentMetricRisk currentRisk = openRisk(null, new BigDecimal("0.10"));
        ExperimentMetricEvaluationReport report = report(
                TrafficStatus.INSUFFICIENT_DATA, comparison(MetricComparisonStatus.NEGATIVE_DEVIATION, currentRisk));

        experimentMetricRiskService.applyEvaluation(runningExperiment(), metricDefinition(), report);

        verify(experimentMetricRiskRepository, never()).save(any());
        verify(experimentMetricRiskRepository, never()).update(any());
        verify(experimentMetricAutoPauseService, never()).pause(any(), any());
    }

    @Test
    void shouldUpdateOpenRiskWhenDeviationWorsens() {
        Instant pausedAt = Instant.parse("2026-04-04T10:15:30Z");
        ExperimentMetricRisk currentRisk = openRisk(null, new BigDecimal("0.10"));
        ExperimentMetricEvaluationReport report =
                report(TrafficStatus.NORMAL, comparison(MetricComparisonStatus.NEGATIVE_DEVIATION, currentRisk));
        AtomicBoolean lockHeld = new AtomicBoolean(false);

        when(lockExecutor.withLock(any(LockNamespace.class), any(String.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    lockHeld.set(true);
                    try {
                        return ((Supplier<?>) invocation.getArgument(2)).get();
                    } finally {
                        lockHeld.set(false);
                    }
                });
        when(experimentMetricAutoPauseService.pause(any(), any())).thenAnswer(invocation -> {
            assertThat(lockHeld.get()).isTrue();
            return pausedAt;
        });

        experimentMetricRiskService.applyEvaluation(runningExperiment(), metricDefinition(), report);

        ArgumentCaptor<ExperimentMetricRisk> riskCaptor = ArgumentCaptor.forClass(ExperimentMetricRisk.class);
        verify(experimentMetricRiskRepository).update(riskCaptor.capture());
        ExperimentMetricRisk updatedRisk = riskCaptor.getValue();

        assertThat(updatedRisk.status()).isEqualTo(ExperimentMetricRiskStatus.OPEN);
        assertThat(updatedRisk.autoPausedAt()).isEqualTo(pausedAt);
        assertThat(updatedRisk.lastBadDeviation()).isEqualByComparingTo("0.20");
        assertThat(updatedRisk.worstBadDeviation()).isEqualByComparingTo("0.20");
    }

    @Test
    void shouldKeepOpenRiskWhenDeviationIsNoLongerNegative() {
        ExperimentMetricRisk currentRisk = openRisk(null, new BigDecimal("0.10"));
        ExperimentMetricEvaluationReport report =
                report(TrafficStatus.NORMAL, comparison(MetricComparisonStatus.NORMAL, currentRisk));

        experimentMetricRiskService.applyEvaluation(runningExperiment(), metricDefinition(), report);

        verify(experimentMetricRiskRepository, never()).save(any());
        verify(experimentMetricRiskRepository, never()).update(any());
        verify(experimentMetricAutoPauseService, never()).pause(any(), any());
    }

    @Test
    void shouldNotAutoPauseAgainWhenRiskWasAlreadyAutoPaused() {
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
                UUID.randomUUID(), "checkout-redesign", "CHECKOUT", List.of(), ExperimentState.RUNNING, 7L, null, null);
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
        return openRisk(UUID.randomUUID(), UUID.randomUUID(), "orders", autoPausedAt, worstBadDeviation);
    }

    private ExperimentMetricRisk openRisk(
            UUID riskId, UUID experimentId, String metricKey, Instant autoPausedAt, BigDecimal worstBadDeviation) {
        Instant openedAt = Instant.parse("2026-04-03T10:15:30Z");
        return new ExperimentMetricRisk(
                riskId,
                experimentId,
                metricKey,
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
