package io.github.rehody.abplatform.evaluation.service;

import io.github.rehody.abplatform.binding.service.ExperimentMetricBindingService;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.service.ExperimentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExperimentMetricEvaluationBatchService {

    private final ExperimentService experimentService;
    private final ExperimentMetricBindingService experimentMetricBindingService;
    private final ExperimentMetricEvaluationService experimentMetricEvaluationService;

    public void evaluateRunningExperiments() {
        List<Experiment> experiments = experimentService.getRunning();
        experiments.forEach(this::evaluateExperiment);
    }

    private void evaluateExperiment(Experiment experiment) {
        List<String> metricKeys = experimentMetricBindingService.getMetricKeys(experiment.id());
        for (String metricKey : metricKeys) {
            try {
                experimentMetricEvaluationService.evaluateAndApplyRisk(experiment, metricKey);
            } catch (RuntimeException ex) {
                log.warn("Failed to evaluate experiment {} metric {}: {}", experiment.id(), metricKey, ex.getMessage());
            }
        }
    }
}
