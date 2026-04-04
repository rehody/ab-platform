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
        ensureNoRunningMetricConflicts(experiment.id(), metricKeys);
    }

    public void ensureNoRunningMetricConflicts(UUID experimentId, List<String> metricKeys) {
        boolean existsConflictingMetricKey =
                experimentMetricBindingRepository.existsConflictingMetricKey(experimentId, metricKeys);

        if (existsConflictingMetricKey) {
            throw new ExperimentActivationConflictException(
                    "Experiment with id %s already exists".formatted(experimentId));
        }
    }
}
