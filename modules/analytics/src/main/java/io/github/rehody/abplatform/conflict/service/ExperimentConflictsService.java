package io.github.rehody.abplatform.conflict.service;

import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.conflict.enums.ExperimentConflictType;
import io.github.rehody.abplatform.conflict.model.ExperimentConflict;
import io.github.rehody.abplatform.conflict.repository.ExperimentConflictRepository;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.service.ExperimentService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExperimentConflictsService {

    private final ExperimentConflictRepository experimentConflictRepository;
    private final ExperimentService experimentService;

    public List<ExperimentConflict> getAll(UUID experimentId) {
        Experiment experiment = experimentService.getById(experimentId);
        return findConflicts(experiment);
    }

    public List<ExperimentConflict> getBlockingConflicts(Experiment experiment) {
        return findConflicts(experiment).stream()
                .filter(conflict -> conflict.severity().isBlocking())
                .toList();
    }

    private List<ExperimentConflict> findConflicts(Experiment experiment) {
        if (experiment.isTerminal()) {
            return List.of();
        }

        return experimentConflictRepository.findAll(experiment.id(), experiment.flagKey(), experiment.domain()).stream()
                .map(conflictingExperiment -> buildConflict(experiment, conflictingExperiment))
                .filter(this::hasConflictTypes)
                .toList();
    }

    private ExperimentConflict buildConflict(Experiment experiment, Experiment conflictingExperiment) {
        return new ExperimentConflict(
                conflictingExperiment.id(),
                conflictingExperiment.state(),
                conflictingExperiment.flagKey(),
                conflictingExperiment.domain(),
                resolveConflictTypes(experiment, conflictingExperiment),
                resolveSeverity(conflictingExperiment));
    }

    private List<ExperimentConflictType> resolveConflictTypes(Experiment experiment, Experiment conflictingExperiment) {
        List<ExperimentConflictType> conflictTypes = new ArrayList<>();

        if (experiment.flagKey().equals(conflictingExperiment.flagKey())) {
            conflictTypes.add(ExperimentConflictType.SAME_FLAG);
        }

        if (experiment.domain().equals(conflictingExperiment.domain())) {
            conflictTypes.add(ExperimentConflictType.DOMAIN_OVERLAP);
        }

        return List.copyOf(conflictTypes);
    }

    private ConflictSeverity resolveSeverity(Experiment conflictingExperiment) {
        if (conflictingExperiment.isRunning()) {
            return ConflictSeverity.BLOCKING;
        }
        return ConflictSeverity.WARNING;
    }

    private boolean hasConflictTypes(ExperimentConflict conflict) {
        return !conflict.conflictTypes().isEmpty();
    }
}
