package io.github.rehody.abplatform.binding.policy;

import io.github.rehody.abplatform.binding.repository.ExperimentMetricBindingRepository;
import io.github.rehody.abplatform.exception.ExperimentActivationConflictException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.policy.ExperimentActivationPolicy;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentMetricBindingConflictPolicy implements ExperimentActivationPolicy {

    private final ExperimentMetricBindingRepository experimentMetricBindingRepository;

    @Override
    public void validateActivation(Experiment experiment) {
        List<String> metricKeys = experimentMetricBindingRepository.findMetricKeysByExperimentId(experiment.id());
        validateNoRunningMetricConflicts(experiment.id(), metricKeys);
    }

    public void validateNoRunningMetricConflicts(UUID experimentId, List<String> metricKeys) {
        List<String> conflictingMetricKeys =
                experimentMetricBindingRepository.findConflictingMetricKeys(experimentId, metricKeys);

        if (!conflictingMetricKeys.isEmpty()) {
            throw new ExperimentActivationConflictException(
                    "Experiment '%s' conflicts with running experiments on metric keys: %s"
                            .formatted(experimentId, String.join(", ", conflictingMetricKeys)),
                    conflictingMetricKeys);
        }
    }
}
