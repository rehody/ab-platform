package io.github.rehody.abplatform.evaluation.service;

import io.github.rehody.abplatform.binding.service.ExperimentMetricBindingService;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.rollout.service.ExperimentRolloutAutomationService;
import io.github.rehody.abplatform.service.ExperimentQueryService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExperimentMetricEvaluationBatchService {

    private final ExperimentQueryService experimentQueryService;
    private final ExperimentMetricBindingService experimentMetricBindingService;
    private final ExperimentMetricEvaluationService experimentMetricEvaluationService;
    private final ExperimentRolloutAutomationService experimentRolloutAutomationService;

    public void evaluateRunningExperiments() {
        List<Experiment> experiments = experimentQueryService.getRunning();
        experiments.forEach(this::evaluateExperiment);
    }

    private void evaluateExperiment(Experiment experiment) {
        List<String> metricKeys = experimentMetricBindingService.getMetricKeys(experiment.id());
        List<ExperimentMetricEvaluationReport> evaluationReports = new ArrayList<>(metricKeys.size());

        for (String metricKey : metricKeys) {
            try {
                evaluationReports.add(
                        experimentMetricEvaluationService.evaluateAndApplyRisk(experiment.id(), metricKey));
            } catch (RuntimeException ex) {
                log.warn("Failed to evaluate experiment {} metric {}: {}", experiment.id(), metricKey, ex.getMessage());
            }
        }

        experimentRolloutAutomationService.apply(experiment, evaluationReports);
    }
}
