package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
import io.github.rehody.abplatform.policy.ExperimentTimestampPolicy;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.UpdateOutcome;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentLifecycleService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentCommandSupport experimentCommandSupport;
    private final ExperimentAssignmentPolicy experimentAssignmentPolicy;
    private final ExperimentTimestampPolicy experimentTimestampPolicy;

    @Transactional
    public Experiment submitForReview(UUID id, long version) {
        return transition(id, version, Experiment::submitForReview);
    }

    @Transactional
    public Experiment approve(UUID id, long version) {
        return transition(id, version, Experiment::approve);
    }

    @Transactional
    public Experiment reject(UUID id, long version) {
        return transition(id, version, Experiment::reject);
    }

    @Transactional
    public Experiment start(UUID id, long version) {
        return transition(id, version, Experiment::start);
    }

    @Transactional
    public Experiment pause(UUID id, long version) {
        return transition(id, version, Experiment::pause);
    }

    @Transactional
    public Experiment resume(UUID id, long version) {
        return transition(id, version, Experiment::resume);
    }

    @Transactional
    public Experiment complete(UUID id, long version) {
        return transition(id, version, Experiment::complete);
    }

    @Transactional
    public Experiment archive(UUID id, long version) {
        return transition(id, version, Experiment::archive);
    }

    private Experiment transition(UUID id, long expectedVersion, UnaryOperator<Experiment> stateTransition) {
        String flagKey = experimentCommandSupport.getFlagKeyById(id);

        return experimentCommandSupport.withExperimentLock(flagKey, () -> {
            Experiment experiment = experimentCommandSupport.getById(id);
            Experiment transitedExperiment = stateTransition.apply(experiment);
            Experiment timestampedExperiment =
                    experimentTimestampPolicy.applyTransitionTimestamps(experiment, transitedExperiment, Instant.now());

            experimentAssignmentPolicy.validateAssignmentInvariants(timestampedExperiment);
            Experiment experimentToUpdate = timestampedExperiment.withVersion(expectedVersion);

            long newVersion = updateAndCheckOptimisticLocking(experimentToUpdate, expectedVersion);
            experimentCommandSupport.invalidateCacheAfterCommit(flagKey);

            return timestampedExperiment.withVersion(newVersion);
        });
    }

    private long updateAndCheckOptimisticLocking(Experiment experiment, long expectedVersion) {
        UpdateOutcome outcome = experimentRepository.update(experiment);
        return switch (outcome.status()) {
            case NOT_FOUND ->
                throw new ExperimentNotFoundException("Experiment '%s' not found".formatted(experiment.id()));
            case VERSION_CONFLICT ->
                throw new OptimisticLockingFailureException("Experiment '%s' version mismatch. Expected version %d"
                        .formatted(experiment.id(), expectedVersion));
            case UPDATED -> outcome.version();
        };
    }
}
