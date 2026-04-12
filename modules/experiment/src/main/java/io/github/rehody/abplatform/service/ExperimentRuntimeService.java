package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.exception.ExperimentRolloutException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditTarget;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.UpdateOutcome;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentRuntimeService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentCommandSupport experimentCommandSupport;
    private final ExperimentLifecycleService experimentLifecycleService;
    private final AuditService auditService;

    @Transactional
    public Experiment advanceRollout(UUID experimentId, long version, AuditActor actor) {
        return applyRolloutAction(
                experimentId,
                version,
                "advance",
                actor,
                AuditAction.EXPERIMENT_ROLLOUT_ADVANCED_MANUAL,
                ExperimentRolloutPlan::canAdvance,
                ExperimentRolloutPlan::advance,
                true);
    }

    @Transactional
    public Experiment rollbackRollout(UUID experimentId, long version, AuditActor actor) {
        return applyRolloutAction(
                experimentId,
                version,
                "rollback",
                actor,
                AuditAction.EXPERIMENT_ROLLOUT_ROLLED_BACK_MANUAL,
                ExperimentRolloutPlan::canRollback,
                ExperimentRolloutPlan::rollback,
                true);
    }

    @Transactional
    public void autoAdvanceRollout(UUID experimentId, AuditActor actor) {
        applyRolloutAction(
                experimentId,
                null,
                "advance",
                actor,
                AuditAction.EXPERIMENT_ROLLOUT_ADVANCED_AUTO,
                ExperimentRolloutPlan::canAdvance,
                ExperimentRolloutPlan::advance,
                false);
    }

    @Transactional
    public void autoRollbackRollout(UUID experimentId, AuditActor actor) {
        applyRolloutAction(
                experimentId,
                null,
                "rollback",
                actor,
                AuditAction.EXPERIMENT_ROLLOUT_ROLLED_BACK_AUTO,
                ExperimentRolloutPlan::canRollback,
                ExperimentRolloutPlan::rollback,
                false);
    }

    @Transactional
    public void pauseOnNegativeAfterRollback(UUID experimentId, AuditActor actor) {
        PauseResolution pauseResolution = resolvePauseCommand(experimentId);
        PauseCommand pauseCommand = pauseResolution.command();

        switch (pauseCommand) {
            case MARK_ONLY -> markNegativeAfterRollback(experimentId);
            case PAUSE_AND_MARK -> {
                Experiment pausedExperiment = experimentLifecycleService.pause(experimentId, pauseResolution.version());
                Experiment updatedExperiment = markNegativeAfterRollback(experimentId);
                writeAutoPauseAudit(actor, experimentId, pausedExperiment, updatedExperiment);
            }
            case NONE -> {}
        }
    }

    private Experiment applyRolloutAction(
            UUID experimentId,
            Long expectedVersion,
            String action,
            AuditActor actor,
            AuditAction auditAction,
            Predicate<ExperimentRolloutPlan> canUpdate,
            UnaryOperator<ExperimentRolloutPlan> update,
            boolean strict) {
        String flagKey = experimentCommandSupport.getFlagKeyById(experimentId);

        return experimentCommandSupport.withExperimentLock(flagKey, () -> {
            Experiment experiment = experimentCommandSupport.getById(experimentId);
            if (!experiment.isRunning()) {
                if (strict) {
                    throw new ExperimentRolloutException(
                            "Cannot %s rollout for experiment in state %s. Allowed source states: [RUNNING]"
                                    .formatted(action, experiment.state()));
                }
                return experiment;
            }

            validateExpectedVersion(experiment, expectedVersion);

            if (!canUpdate.test(experiment.rolloutPlan())) {
                if (strict) {
                    throw new ExperimentRolloutException("Cannot %s rollout for experiment %s at step %d"
                            .formatted(
                                    action,
                                    experiment.id(),
                                    experiment.rolloutPlan().regularRolloutPercentage()));
                }
                return experiment;
            }

            ExperimentRolloutPlan updatedRolloutPlan = update.apply(experiment.rolloutPlan());
            Experiment updatedExperiment = experiment.withRolloutPlan(updatedRolloutPlan);

            long updatedVersion = persist(flagKey, updatedExperiment);
            Experiment persistedExperiment = updatedExperiment.withVersion(updatedVersion);
            writeRolloutAudit(actor, auditAction, experiment, persistedExperiment);
            return persistedExperiment;
        });
    }

    private Experiment markNegativeAfterRollback(UUID experimentId) {
        String flagKey = experimentCommandSupport.getFlagKeyById(experimentId);

        return experimentCommandSupport.withExperimentLock(flagKey, () -> {
            Experiment experiment = experimentCommandSupport.getById(experimentId);
            if (!experiment.isPaused()) {
                return experiment;
            }

            Experiment updatedExperiment =
                    experiment.withRolloutPlan(experiment.rolloutPlan().markNegativeAfterRollback());

            if (updatedExperiment.equals(experiment)) {
                return experiment;
            }

            long updatedVersion = persist(flagKey, updatedExperiment);
            return updatedExperiment.withVersion(updatedVersion);
        });
    }

    private PauseResolution resolvePauseCommand(UUID experimentId) {
        String flagKey = experimentCommandSupport.getFlagKeyById(experimentId);

        return experimentCommandSupport.withExperimentLock(flagKey, () -> {
            Experiment experiment = experimentCommandSupport.getById(experimentId);
            if (experiment.isPaused()) {
                return new PauseResolution(PauseCommand.MARK_ONLY);
            }

            if (!experiment.isRunning()) {
                return new PauseResolution(PauseCommand.NONE);
            }

            return new PauseResolution(PauseCommand.PAUSE_AND_MARK, experiment.version());
        });
    }

    private void validateExpectedVersion(Experiment experiment, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }

        if (experiment.version() != expectedVersion) {
            throw new OptimisticLockingFailureException("Experiment '%s' version mismatch. Expected version %d"
                    .formatted(experiment.id(), expectedVersion));
        }
    }

    private long persist(String flagKey, Experiment experiment) {
        UpdateOutcome outcome = experimentRepository.update(experiment);
        return switch (outcome.status()) {
            case NOT_FOUND ->
                throw new ExperimentNotFoundException("Experiment '%s' not found".formatted(experiment.id()));
            case VERSION_CONFLICT ->
                throw new OptimisticLockingFailureException("Experiment '%s' version mismatch. Expected version %d"
                        .formatted(experiment.id(), experiment.version()));
            case UPDATED -> {
                experimentCommandSupport.invalidateCacheAfterCommit(flagKey);
                yield outcome.version();
            }
        };
    }

    private enum PauseCommand {
        NONE,
        MARK_ONLY,
        PAUSE_AND_MARK
    }

    private record PauseResolution(PauseCommand command, Long version) {
        private PauseResolution(PauseCommand command) {
            this(command, null);
        }
    }

    private AuditDetails buildAutoPauseDetails(Experiment pausedExperiment, Experiment updatedExperiment) {
        AuditDetails details = AuditDetails.stateTransition(ExperimentState.RUNNING, pausedExperiment.state());

        if (updatedExperiment.rolloutPlan().stillNegativeAfterRollback()) {
            details = details.with("reason", "auto pause after negative evaluation following rollback");
        }

        return details;
    }

    private void writeRolloutAudit(
            AuditActor actor, AuditAction auditAction, Experiment currentExperiment, Experiment persistedExperiment) {
        auditService.write(
                actor,
                auditAction,
                AuditTarget.experiment(persistedExperiment.id()),
                AuditDetails.rolloutTransition(
                        currentExperiment.rolloutPlan().regularRolloutPercentage(),
                        persistedExperiment.rolloutPlan().regularRolloutPercentage()));
    }

    private void writeAutoPauseAudit(
            AuditActor actor, UUID experimentId, Experiment pausedExperiment, Experiment updatedExperiment) {
        auditService.write(
                actor,
                AuditAction.EXPERIMENT_PAUSED_AUTO_AFTER_ROLLOUT_ROLLBACK,
                AuditTarget.experiment(experimentId),
                buildAutoPauseDetails(pausedExperiment, updatedExperiment));
    }
}
