package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.cache.ExperimentCache;
import io.github.rehody.abplatform.dto.request.ExperimentStateTransitionRequest;
import io.github.rehody.abplatform.dto.response.ExperimentResponse;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
import io.github.rehody.abplatform.policy.ExperimentTimestampPolicy;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.UpdateOutcome;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentLifecycleService {

    private static final LockNamespace EXPERIMENT_LOCK_NAMESPACE = LockNamespace.of("experiment");

    private final ExperimentRepository experimentRepository;
    private final LockExecutor lockExecutor;
    private final ServiceActionExecutor serviceActionExecutor;
    private final ExperimentCache experimentCache;
    private final ExperimentAssignmentPolicy experimentAssignmentPolicy;
    private final ExperimentTimestampPolicy experimentTimestampPolicy;

    @Transactional
    public ExperimentResponse submitForReview(UUID id, ExperimentStateTransitionRequest request) {
        return transition(id, request.version(), Experiment::submitForReview);
    }

    @Transactional
    public ExperimentResponse approve(UUID id, ExperimentStateTransitionRequest request) {
        return transition(id, request.version(), Experiment::approve);
    }

    @Transactional
    public ExperimentResponse reject(UUID id, ExperimentStateTransitionRequest request) {
        return transition(id, request.version(), Experiment::reject);
    }

    @Transactional
    public ExperimentResponse start(UUID id, ExperimentStateTransitionRequest request) {
        return transition(id, request.version(), Experiment::start);
    }

    @Transactional
    public ExperimentResponse pause(UUID id, ExperimentStateTransitionRequest request) {
        return transition(id, request.version(), Experiment::pause);
    }

    @Transactional
    public ExperimentResponse resume(UUID id, ExperimentStateTransitionRequest request) {
        return transition(id, request.version(), Experiment::resume);
    }

    @Transactional
    public ExperimentResponse complete(UUID id, ExperimentStateTransitionRequest request) {
        return transition(id, request.version(), Experiment::complete);
    }

    @Transactional
    public ExperimentResponse archive(UUID id, ExperimentStateTransitionRequest request) {
        return transition(id, request.version(), Experiment::archive);
    }

    private ExperimentResponse transition(UUID id, long expectedVersion, UnaryOperator<Experiment> stateTransition) {
        String flagKey = findFlagKeyByIdOrThrow(id);

        return executeUnderLock(flagKey, () -> {
            Experiment experiment = findByIdOrThrow(id);
            Experiment transitedExperiment = stateTransition.apply(experiment);
            Experiment timestampedExperiment =
                    experimentTimestampPolicy.applyTransitionTimestamps(experiment, transitedExperiment, Instant.now());
            experimentAssignmentPolicy.validateAssignmentInvariants(timestampedExperiment);
            Experiment experimentToUpdate = timestampedExperiment.withVersion(expectedVersion);

            long newVersion = updateAndCheckOptimisticLocking(experimentToUpdate, expectedVersion);
            invalidateCacheAfterCommit(flagKey);

            Experiment persistedExperiment = timestampedExperiment.withVersion(newVersion);
            return ExperimentResponse.from(persistedExperiment);
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

    private Experiment findByIdOrThrow(UUID id) {
        return experimentRepository
                .findById(id)
                .orElseThrow(() -> new ExperimentNotFoundException("Experiment '%s' not found".formatted(id)));
    }

    private String findFlagKeyByIdOrThrow(UUID id) {
        return experimentRepository
                .findFlagKeyById(id)
                .orElseThrow(() -> new ExperimentNotFoundException("Experiment '%s' not found".formatted(id)));
    }

    private void invalidateCacheAfterCommit(String flagKey) {
        serviceActionExecutor.executeAfterCommit(() -> experimentCache.invalidate(flagKey));
    }

    private <T> T executeUnderLock(String key, Supplier<T> action) {
        return lockExecutor.withLock(EXPERIMENT_LOCK_NAMESPACE, key, action);
    }
}
