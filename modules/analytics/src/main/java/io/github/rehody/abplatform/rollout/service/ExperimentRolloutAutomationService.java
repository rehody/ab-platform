package io.github.rehody.abplatform.rollout.service;

import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.rollout.enums.ExperimentRolloutDecision;
import io.github.rehody.abplatform.rollout.policy.ExperimentRolloutPolicy;
import io.github.rehody.abplatform.service.ExperimentRuntimeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExperimentRolloutAutomationService {

    private final ExperimentRolloutPolicy experimentRolloutPolicy;
    private final ExperimentRuntimeService experimentRuntimeService;

    public void apply(Experiment experiment, List<ExperimentMetricEvaluationReport> evaluationReports) {
        ExperimentRolloutDecision decision = experimentRolloutPolicy.decide(experiment, evaluationReports);

        switch (decision) {
            case ADVANCE -> experimentRuntimeService.advanceRollout(experiment.id());
            case ROLLBACK -> experimentRuntimeService.rollbackRollout(experiment.id());
            case PAUSE -> experimentRuntimeService.pauseOnNegativeAfterRollback(experiment.id());
        }
    }
}
