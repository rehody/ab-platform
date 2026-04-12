package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditTarget;
import io.github.rehody.abplatform.policy.ExperimentActivationPolicy;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
import io.github.rehody.abplatform.policy.ExperimentTimestampPolicy;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.UpdateOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
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
    private final AuditService auditService;

    @Transactional
    public Experiment submitForReview(UUID id, long version, AuditActor actor) {
        return transition(id, version, actor, AuditAction.EXPERIMENT_SUBMITTED_FOR_REVIEW, Experiment::submitForReview);
    }

    @Transactional
    public Experiment approve(UUID id, long version, AuditActor actor) {
        return activate(id, version, actor, AuditAction.EXPERIMENT_APPROVED, Experiment::approve);
    }

    @Transactional
    public Experiment reject(UUID id, long version, AuditActor actor) {
        return transition(id, version, actor, AuditAction.EXPERIMENT_REJECTED, Experiment::reject);
    }

    @Transactional
    public Experiment start(UUID id, long version, AuditActor actor) {
        return activate(id, version, actor, AuditAction.EXPERIMENT_STARTED, Experiment::start);
    }

    @Transactional
    public Experiment pause(UUID id, long version, AuditActor actor) {
        return transition(id, version, actor, AuditAction.EXPERIMENT_PAUSED, Experiment::pause);
    }

    @Transactional
    public Experiment pause(UUID id, long version) {
        return transition(id, version, null, null, Experiment::pause);
    }

    @Transactional
    public Experiment resume(UUID id, long version, AuditActor actor) {
        return activate(id, version, actor, AuditAction.EXPERIMENT_RESUMED, Experiment::resume);
    }

    @Transactional
    public Experiment complete(UUID id, long version, AuditActor actor) {
        return transition(id, version, actor, AuditAction.EXPERIMENT_COMPLETED, Experiment::complete);
    }

    @Transactional
    public Experiment archive(UUID id, long version, AuditActor actor) {
        return transition(id, version, actor, AuditAction.EXPERIMENT_ARCHIVED, Experiment::archive);
    }

    private Experiment transition(
            UUID id,
            long expectedVersion,
            AuditActor actor,
            AuditAction auditAction,
            UnaryOperator<Experiment> stateTransition) {
        return withLockedExperiment(id, lockedExperiment -> {
            Experiment transitionedExperiment =
                    buildTransitionedExperiment(lockedExperiment.experiment(), stateTransition);

            experimentAssignmentPolicy.validateAssignmentInvariants(transitionedExperiment);
            return persistTransition(
                    lockedExperiment.flagKey(),
                    lockedExperiment.experiment(),
                    transitionedExperiment,
                    expectedVersion,
                    actor,
                    auditAction);
        });
    }

    private Experiment activate(
            UUID id,
            long expectedVersion,
            AuditActor actor,
            AuditAction auditAction,
            UnaryOperator<Experiment> stateTransition) {
        return withLockedExperiment(id, lockedExperiment -> {
            Experiment transitionedExperiment =
                    buildTransitionedExperiment(lockedExperiment.experiment(), stateTransition);

            validateActivationPolicies(transitionedExperiment);
            experimentAssignmentPolicy.validateAssignmentInvariants(transitionedExperiment);
            return persistTransition(
                    lockedExperiment.flagKey(),
                    lockedExperiment.experiment(),
                    transitionedExperiment,
                    expectedVersion,
                    actor,
                    auditAction);
        });
    }

    private Experiment withLockedExperiment(UUID id, Function<LockedExperiment, Experiment> action) {
        String flagKey = experimentCommandSupport.getFlagKeyById(id);

        return experimentCommandSupport.withExperimentLock(flagKey, () -> {
            Experiment experiment = experimentCommandSupport.getById(id);
            return action.apply(new LockedExperiment(flagKey, experiment));
        });
    }

    private void validateActivationPolicies(Experiment experiment) {
        for (ExperimentActivationPolicy experimentActivationPolicy : experimentActivationPolicies) {
            experimentActivationPolicy.validateActivation(experiment);
        }
    }

    private Experiment buildTransitionedExperiment(
            Experiment currentExperiment, UnaryOperator<Experiment> stateTransition) {
        Experiment transitionedExperiment = stateTransition.apply(currentExperiment);
        return experimentTimestampPolicy.applyTransitionTimestamps(
                currentExperiment, transitionedExperiment, Instant.now());
    }

    private Experiment persistTransition(
            String flagKey,
            Experiment currentExperiment,
            Experiment experiment,
            long expectedVersion,
            AuditActor actor,
            AuditAction auditAction) {
        Experiment experimentToUpdate = experiment.withVersion(expectedVersion);
        long newVersion = updateExperiment(experimentToUpdate, expectedVersion);
        experimentCommandSupport.invalidateCacheAfterCommit(flagKey);
        Experiment persistedExperiment = experiment.withVersion(newVersion);
        writeTransitionAudit(actor, auditAction, currentExperiment, persistedExperiment);
        return persistedExperiment;
    }

    private long updateExperiment(Experiment experiment, long expectedVersion) {
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

    private void writeTransitionAudit(
            AuditActor actor, AuditAction auditAction, Experiment currentExperiment, Experiment persistedExperiment) {
        if (actor == null || auditAction == null) {
            return;
        }

        auditService.write(
                actor,
                auditAction,
                AuditTarget.experiment(persistedExperiment.id()),
                AuditDetails.stateTransition(currentExperiment.state(), persistedExperiment.state()));
    }

    private record LockedExperiment(String flagKey, Experiment experiment) {}
}
