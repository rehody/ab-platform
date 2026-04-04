package io.github.rehody.abplatform.evaluation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentMetricEvaluationScheduler {

    private final ExperimentMetricEvaluationBatchService experimentMetricEvaluationBatchService;

    @Scheduled(fixedDelayString = "#{@analyticsEvaluationProperties.evaluationInterval.toMillis()}")
    public void evaluateRunningExperiments() {
        experimentMetricEvaluationBatchService.evaluateRunningExperiments();
    }
}
