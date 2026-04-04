package io.github.rehody.abplatform.risk.service;

import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.VariantComparison;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.risk.exception.ExperimentMetricRiskNotFoundException;
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
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentMetricRiskService {

    private static final LockNamespace EXPERIMENT_METRIC_RISK_LOCK_NAMESPACE =
            LockNamespace.of("experiment-metric-risk");

    private final ExperimentMetricRiskRepository experimentMetricRiskRepository;
    private final ExperimentMetricRiskPolicy experimentMetricRiskPolicy;
    private final ExperimentMetricRiskFactory experimentMetricRiskFactory;
    private final ExperimentMetricAutoPauseService experimentMetricAutoPauseService;
    private final LockExecutor lockExecutor;

    @Transactional(readOnly = true)
    public List<ExperimentMetricRisk> getRisks(UUID experimentId, String metricKey) {
        return experimentMetricRiskRepository.findByExperimentAndMetric(experimentId, metricKey);
    }

    @Transactional
    public ExperimentMetricRisk resolve(UUID riskId, String comment) {
        ExperimentMetricRisk currentRisk = getRiskById(riskId);

        return executeUnderLock(currentRisk.experimentId(), currentRisk.metricKey(), () -> {
            ExperimentMetricRisk lockedRisk = getRiskById(riskId);
            ExperimentMetricRisk resolvedRisk =
                    experimentMetricRiskFactory.createResolved(lockedRisk, comment, Instant.now());

            experimentMetricRiskRepository.update(resolvedRisk);
            return resolvedRisk;
        });
    }

    @Transactional
    public void applyEvaluation(
            Experiment experiment, MetricDefinition metricDefinition, ExperimentMetricEvaluationReport report) {
        executeUnderLock(experiment.id(), metricDefinition.key(), () -> {
            applyEvaluationUnderLock(experiment, metricDefinition, report);
            return null;
        });
    }

    private void applyEvaluationUnderLock(
            Experiment experiment, MetricDefinition metricDefinition, ExperimentMetricEvaluationReport report) {
        if (!report.traffic().isNormal()) {
            return;
        }

        for (VariantComparison comparison : report.comparisons()) {
            ExperimentMetricRisk currentRisk = comparison.risk();
            if (!comparison.hasNegativeDeviation()) {
                continue;
            }

            BigDecimal badDeviation = experimentMetricRiskPolicy.toBadDeviation(
                    metricDefinition.direction(), comparison.relativeDeviation());

            if (currentRisk == null) {
                createOpenRisk(experiment, metricDefinition, comparison, badDeviation);
                continue;
            }

            if (currentRisk.isResolved()) {
                reopenRisk(currentRisk, badDeviation);
                continue;
            }

            updateOpenRisk(experiment, currentRisk, badDeviation);
        }
    }

    private void createOpenRisk(
            Experiment experiment,
            MetricDefinition metricDefinition,
            VariantComparison comparison,
            BigDecimal badDeviation) {
        ExperimentMetricRisk risk = experimentMetricRiskFactory.createOpen(
                experiment, metricDefinition, comparison, badDeviation, Instant.now());
        experimentMetricRiskRepository.save(risk);
    }

    private void reopenRisk(ExperimentMetricRisk currentRisk, BigDecimal badDeviation) {
        ExperimentMetricRisk reopenedRisk =
                experimentMetricRiskFactory.reopen(currentRisk, badDeviation, Instant.now());
        experimentMetricRiskRepository.update(reopenedRisk);
    }

    private void updateOpenRisk(Experiment experiment, ExperimentMetricRisk currentRisk, BigDecimal badDeviation) {
        boolean isWorsening = experimentMetricRiskPolicy.isWorsening(currentRisk, badDeviation);
        Instant autoPausedAt = currentRisk.autoPausedAt();
        BigDecimal worstBadDeviation = currentRisk.worstBadDeviation();

        if (isWorsening) {
            worstBadDeviation = badDeviation;
            if (shouldAutoPause(experiment, currentRisk)) {
                autoPausedAt = experimentMetricAutoPauseService.pause(experiment, currentRisk);
            }
        }

        ExperimentMetricRisk updatedRisk = experimentMetricRiskFactory.refreshOpenRisk(
                currentRisk, badDeviation, worstBadDeviation, autoPausedAt, Instant.now());

        experimentMetricRiskRepository.update(updatedRisk);
    }

    private boolean shouldAutoPause(Experiment experiment, ExperimentMetricRisk currentRisk) {
        return experiment.isRunning() && currentRisk.autoPausedAt() == null;
    }

    private ExperimentMetricRisk getRiskById(UUID riskId) {
        return experimentMetricRiskRepository
                .findById(riskId)
                .orElseThrow(() -> new ExperimentMetricRiskNotFoundException(
                        "Experiment metric risk '%s' not found".formatted(riskId)));
    }

    private String buildRiskLockKey(UUID experimentId, String metricKey) {
        return "%s:%s".formatted(experimentId, metricKey);
    }

    private <T> T executeUnderLock(UUID experimentId, String metricKey, Supplier<T> action) {
        return lockExecutor.withLock(
                EXPERIMENT_METRIC_RISK_LOCK_NAMESPACE, buildRiskLockKey(experimentId, metricKey), action);
    }
}
