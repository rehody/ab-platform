package io.github.rehody.abplatform.evaluation.service;

import io.github.rehody.abplatform.evaluation.factory.ExperimentMetricRiskFactory;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.VariantComparison;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.evaluation.policy.ExperimentMetricRiskPolicy;
import io.github.rehody.abplatform.evaluation.repository.ExperimentMetricRiskRepository;
import io.github.rehody.abplatform.exception.ExperimentMetricRiskNotFoundException;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentMetricRiskService {

    private final ExperimentMetricRiskRepository experimentMetricRiskRepository;
    private final ExperimentMetricRiskPolicy experimentMetricRiskPolicy;
    private final ExperimentMetricRiskFactory experimentMetricRiskFactory;
    private final ExperimentMetricAutoPauseService experimentMetricAutoPauseService;

    @Transactional(readOnly = true)
    public List<ExperimentMetricRisk> getRisks(UUID experimentId, String metricKey) {
        return experimentMetricRiskRepository.findByExperimentAndMetric(experimentId, metricKey);
    }

    @Transactional
    public ExperimentMetricRisk resolve(UUID riskId, String comment) {
        ExperimentMetricRisk currentRisk = experimentMetricRiskRepository
                .findById(riskId)
                .orElseThrow(() -> new ExperimentMetricRiskNotFoundException(
                        "Experiment metric risk '%s' not found".formatted(riskId)));

        String normalizedComment = normalizeComment(comment);
        ExperimentMetricRisk resolvedRisk =
                experimentMetricRiskFactory.createResolved(currentRisk, normalizedComment, Instant.now());

        experimentMetricRiskRepository.update(resolvedRisk);
        return resolvedRisk;
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return "";
        }
        return comment.trim();
    }

    @Transactional
    public void applyEvaluation(
            Experiment experiment, MetricDefinition metricDefinition, ExperimentMetricEvaluationReport report) {
        if (!report.traffic().isNormal()) {
            return;
        }

        for (VariantComparison comparison : report.comparisons()) {
            ExperimentMetricRisk currentRisk = comparison.risk();
            if (!comparison.hasNegativeDeviation()) {
                closeOpenRiskIfRecovered(currentRisk);
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

    private void closeOpenRiskIfRecovered(ExperimentMetricRisk currentRisk) {
        if (currentRisk == null || !currentRisk.isOpen()) {
            return;
        }

        ExperimentMetricRisk resolvedRisk =
                experimentMetricRiskFactory.createResolved(currentRisk, null, Instant.now());

        experimentMetricRiskRepository.update(resolvedRisk);
    }

    private boolean shouldAutoPause(Experiment experiment, ExperimentMetricRisk currentRisk) {
        return experiment.isRunning() && currentRisk.autoPausedAt() == null;
    }
}
