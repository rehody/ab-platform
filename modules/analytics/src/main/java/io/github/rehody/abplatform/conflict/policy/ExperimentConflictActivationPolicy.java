package io.github.rehody.abplatform.conflict.policy;

import io.github.rehody.abplatform.conflict.model.ExperimentConflict;
import io.github.rehody.abplatform.conflict.service.ExperimentConflictsService;
import io.github.rehody.abplatform.exception.ExperimentBlockingConflictDetails;
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

        List<ExperimentBlockingConflictDetails> blockingConflictDetails = getBlockingConflictDetails(blockingConflicts);
        throw new ExperimentBlockingConflictException(
                blockingConflictMessage(experiment.id(), blockingConflictDetails), blockingConflictDetails);
    }

    private String blockingConflictMessage(UUID experimentId, List<ExperimentBlockingConflictDetails> conflicts) {
        return "Experiment '%s' has blocking conflicts with running experiments: %s"
                .formatted(experimentId, String.join(", ", getConflictingExperimentIds(conflicts)));
    }

    private List<ExperimentBlockingConflictDetails> getBlockingConflictDetails(
            List<ExperimentConflict> blockingConflicts) {
        return blockingConflicts.stream()
                .map(conflict -> new ExperimentBlockingConflictDetails(
                        conflict.experimentId(),
                        conflict.state(),
                        conflict.flagKey(),
                        conflict.domainKey(),
                        conflict.conflictTypes().stream().map(Enum::name).toList(),
                        conflict.severity().name()))
                .toList();
    }

    private List<String> getConflictingExperimentIds(List<ExperimentBlockingConflictDetails> conflicts) {
        return conflicts.stream()
                .map(conflict -> conflict.experimentId().toString())
                .distinct()
                .sorted()
                .toList();
    }
}
