package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.exception.ExperimentRolloutException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
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

    @Transactional
    public Experiment advanceRollout(UUID experimentId, long version) {
        return applyRolloutAction(
                experimentId,
                version,
                "advance",
                ExperimentRolloutPlan::canAdvance,
                ExperimentRolloutPlan::advance,
                true);
    }

    @Transactional
    public Experiment rollbackRollout(UUID experimentId, long version) {
        return applyRolloutAction(
                experimentId,
                version,
                "rollback",
                ExperimentRolloutPlan::canRollback,
                ExperimentRolloutPlan::rollback,
                true);
    }

    @Transactional
    public void autoAdvanceRollout(UUID experimentId) {
        applyRolloutAction(
                experimentId,
                null,
                "advance",
                ExperimentRolloutPlan::canAdvance,
                ExperimentRolloutPlan::advance,
                false);
    }

    @Transactional
    public void autoRollbackRollout(UUID experimentId) {
        applyRolloutAction(
                experimentId,
                null,
                "rollback",
                ExperimentRolloutPlan::canRollback,
                ExperimentRolloutPlan::rollback,
                false);
    }

    @Transactional
    public void pauseOnNegativeAfterRollback(UUID experimentId) {
        PauseResolution pauseResolution = resolvePauseCommand(experimentId);
        PauseCommand pauseCommand = pauseResolution.command();

        switch (pauseCommand) {
            case MARK_ONLY -> markNegativeAfterRollback(experimentId);
            case PAUSE_AND_MARK -> {
                experimentLifecycleService.pause(experimentId, pauseResolution.version());
                markNegativeAfterRollback(experimentId);
            }
        }
    }

    private Experiment applyRolloutAction(
            UUID experimentId,
            Long expectedVersion,
            String action,
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
            return updatedExperiment.withVersion(updatedVersion);
        });
    }

    private void markNegativeAfterRollback(UUID experimentId) {
        String flagKey = experimentCommandSupport.getFlagKeyById(experimentId);

        experimentCommandSupport.withExperimentLock(flagKey, () -> {
            Experiment experiment = experimentCommandSupport.getById(experimentId);
            if (!experiment.isPaused()) {
                return null;
            }

            Experiment updatedExperiment =
                    experiment.withRolloutPlan(experiment.rolloutPlan().markNegativeAfterRollback());

            if (updatedExperiment.equals(experiment)) {
                return null;
            }

            persist(flagKey, updatedExperiment);
            return null;
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
}
