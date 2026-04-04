package io.github.rehody.abplatform.conflict.policy;

import io.github.rehody.abplatform.conflict.model.ExperimentConflict;
import io.github.rehody.abplatform.conflict.service.ExperimentConflictsService;
import io.github.rehody.abplatform.exception.ExperimentBlockingConflictException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.policy.ExperimentActivationPolicy;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentConflictActivationPolicy implements ExperimentActivationPolicy {

    private final ExperimentConflictsService experimentConflictsService;

    @Override
    public void validateActivation(Experiment experiment) {
        List<ExperimentConflict> blockingConflicts = experimentConflictsService.getBlockingConflicts(experiment);
        if (blockingConflicts.isEmpty()) {
            return;
        }

        List<String> blockingExperimentIds = blockingExperimentIds(blockingConflicts);
        throw new ExperimentBlockingConflictException(
                blockingConflictMessage(experiment.id(), blockingExperimentIds), blockingExperimentIds);
    }

    private String blockingConflictMessage(UUID experimentId, List<String> blockingExperimentIds) {
        return "Experiment '%s' has blocking conflicts with running experiments: %s"
                .formatted(experimentId, String.join(", ", blockingExperimentIds));
    }

    private List<String> blockingExperimentIds(List<ExperimentConflict> blockingConflicts) {
        return blockingConflicts.stream()
                .map(conflict -> conflict.experimentId().toString())
                .distinct()
                .sorted()
                .toList();
    }
}
