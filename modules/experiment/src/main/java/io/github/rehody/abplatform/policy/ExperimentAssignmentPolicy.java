package io.github.rehody.abplatform.policy;

import io.github.rehody.abplatform.model.Experiment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentAssignmentPolicy {

    private final ExperimentVariantPolicy experimentVariantPolicy;

    public boolean canResolveAssignment(Experiment experiment) {
        return experiment.isRunning();
    }

    public void validateAssignmentInvariants(Experiment experiment) {
        if (canResolveAssignment(experiment)) {
            experimentVariantPolicy.validateResolvableVariantConfiguration(experiment.id(), experiment.variants());
        }
    }
}
