package io.github.rehody.abplatform.conflict.service;

import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.conflict.enums.ExperimentConflictType;
import io.github.rehody.abplatform.conflict.model.ExperimentConflict;
import io.github.rehody.abplatform.conflict.repository.ExperimentConflictRepository;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.service.ExperimentService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExperimentConflictsService {

    private final ExperimentConflictRepository experimentConflictRepository;
    private final ExperimentService experimentService;
    private final ExperimentConflictSeverityResolver experimentConflictSeverityResolver;

    public List<ExperimentConflict> getAll(UUID experimentId) {
        Experiment experiment = experimentService.getById(experimentId);
        return getAll(experiment);
    }

    public List<ExperimentConflict> getAll(Experiment experiment) {
        return findConflicts(experiment);
    }

    public List<ExperimentConflict> getBlockingConflicts(Experiment experiment) {
        return getAll(experiment).stream()
                .filter(conflict -> conflict.severity().isBlocking())
                .toList();
    }

    private List<ExperimentConflict> findConflicts(Experiment experiment) {
        if (!isConflictRelevant(experiment)) {
            return List.of();
        }

        return experimentConflictRepository
                .findAll(experiment.id(), experiment.flagKey(), experiment.domainKey())
                .stream()
                .map(conflictingExperiment -> buildConflict(experiment, conflictingExperiment))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<ExperimentConflict> buildConflict(Experiment experiment, Experiment conflictingExperiment) {
        ConflictSeverity severity = experimentConflictSeverityResolver.resolve(conflictingExperiment.state());
        if (severity.isNone()) {
            return Optional.empty();
        }

        List<ExperimentConflictType> conflictTypes = resolveConflictTypes(experiment, conflictingExperiment);
        if (conflictTypes.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ExperimentConflict(
                conflictingExperiment.id(),
                conflictingExperiment.state(),
                conflictingExperiment.flagKey(),
                conflictingExperiment.domainKey(),
                conflictTypes,
                severity));
    }

    private List<ExperimentConflictType> resolveConflictTypes(Experiment experiment, Experiment conflictingExperiment) {
        List<ExperimentConflictType> conflictTypes = new ArrayList<>();

        if (experiment.flagKey().equals(conflictingExperiment.flagKey())) {
            conflictTypes.add(ExperimentConflictType.SAME_FLAG);
        }

        if (experiment.domainKey().equals(conflictingExperiment.domainKey())) {
            conflictTypes.add(ExperimentConflictType.DOMAIN_OVERLAP);
        }

        return List.copyOf(conflictTypes);
    }

    private boolean isConflictRelevant(Experiment experiment) {
        return experimentConflictSeverityResolver.resolve(experiment.state()) != ConflictSeverity.NONE;
    }
}
