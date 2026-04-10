package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.UpdateOutcome;
import java.util.UUID;
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
    public void advanceRollout(UUID experimentId) {
        updateRollout(experimentId, experiment -> {
            ExperimentRolloutPlan advanced = experiment.rolloutPlan().advance();
            return experiment.withRolloutPlan(advanced);
        });
    }

    @Transactional
    public void rollbackRollout(UUID experimentId) {
        updateRollout(experimentId, experiment -> {
            ExperimentRolloutPlan rolledBack = experiment.rolloutPlan().rollback();
            return experiment.withRolloutPlan(rolledBack);
        });
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

    private void updateRollout(UUID experimentId, UnaryOperator<Experiment> update) {
        String flagKey = experimentCommandSupport.getFlagKeyById(experimentId);

        experimentCommandSupport.withExperimentLock(flagKey, () -> {
            Experiment experiment = experimentCommandSupport.getById(experimentId);
            if (!experiment.isRunning()) {
                return null;
            }

            Experiment updatedExperiment = update.apply(experiment);
            if (updatedExperiment.equals(experiment)) {
                return null;
            }

            persist(flagKey, updatedExperiment);
            return null;
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

    private void persist(String flagKey, Experiment experiment) {
        UpdateOutcome outcome = experimentRepository.update(experiment);
        switch (outcome.status()) {
            case NOT_FOUND ->
                throw new ExperimentNotFoundException("Experiment '%s' not found".formatted(experiment.id()));
            case VERSION_CONFLICT ->
                throw new OptimisticLockingFailureException("Experiment '%s' version mismatch. Expected version %d"
                        .formatted(experiment.id(), experiment.version()));
            case UPDATED -> experimentCommandSupport.invalidateCacheAfterCommit(flagKey);
        }
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
