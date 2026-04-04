package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.policy.ExperimentActivationPolicy;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
import io.github.rehody.abplatform.policy.ExperimentTimestampPolicy;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.UpdateOutcome;
import java.time.Instant;
import java.util.List;
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
    private final List<ExperimentActivationPolicy> experimentActivationPolicies;
    private final ExperimentAssignmentPolicy experimentAssignmentPolicy;
    private final ExperimentTimestampPolicy experimentTimestampPolicy;

    @Transactional
    public Experiment submitForReview(UUID id, long version) {
        return transition(id, version, Experiment::submitForReview);
    }

    @Transactional
    public Experiment approve(UUID id, long version) {
        return transition(id, version, Experiment::approve, true);
    }

    @Transactional
    public Experiment reject(UUID id, long version) {
        return transition(id, version, Experiment::reject);
    }

    @Transactional
    public Experiment start(UUID id, long version) {
        return transition(id, version, Experiment::start, true);
    }

    @Transactional
    public Experiment pause(UUID id, long version) {
        return transition(id, version, Experiment::pause);
    }

    @Transactional
    public Experiment resume(UUID id, long version) {
        return transition(id, version, Experiment::resume, true);
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
        return transition(id, expectedVersion, stateTransition, false);
    }

    private Experiment transition(
            UUID id,
            long expectedVersion,
            UnaryOperator<Experiment> stateTransition,
            boolean validateBlockingConflicts) {
        String flagKey = experimentCommandSupport.getFlagKeyById(id);

        return experimentCommandSupport.withExperimentLock(flagKey, () -> {
            Experiment experiment = experimentCommandSupport.getById(id);
            Experiment transitedExperiment = stateTransition.apply(experiment);
            Experiment timestampedExperiment =
                    experimentTimestampPolicy.applyTransitionTimestamps(experiment, transitedExperiment, Instant.now());

            validateActivationIfNeeded(timestampedExperiment, validateBlockingConflicts);
            experimentAssignmentPolicy.validateAssignmentInvariants(timestampedExperiment);
            Experiment experimentToUpdate = timestampedExperiment.withVersion(expectedVersion);

            long newVersion = updateAndCheckOptimisticLocking(experimentToUpdate, expectedVersion);
            experimentCommandSupport.invalidateCacheAfterCommit(flagKey);

            return timestampedExperiment.withVersion(newVersion);
        });
    }

    private void validateActivationIfNeeded(Experiment experiment, boolean shouldValidateBlockingConflicts) {
        if (!shouldValidateBlockingConflicts) {
            return;
        }

        for (ExperimentActivationPolicy experimentActivationPolicy : experimentActivationPolicies) {
            experimentActivationPolicy.validateActivation(experiment);
        }
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
