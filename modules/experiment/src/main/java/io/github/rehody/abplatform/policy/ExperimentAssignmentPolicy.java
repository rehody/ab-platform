package io.github.rehody.abplatform.policy;

import io.github.rehody.abplatform.model.Experiment;
import org.springframework.stereotype.Component;

@Component
public class ExperimentAssignmentPolicy {

    public boolean canResolveAssignment(Experiment experiment) {
        return experiment.isRunning();
    }

    public void validateAssignmentInvariants(Experiment experiment) {
        if (canResolveAssignment(experiment) && experiment.variants().isEmpty()) {
            throw new IllegalStateException(
                    "Running experiment %s must have at least one variant".formatted(experiment.id()));
        }
    }
}
