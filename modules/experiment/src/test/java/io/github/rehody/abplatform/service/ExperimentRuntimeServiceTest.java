package io.github.rehody.abplatform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.exception.ExperimentRolloutException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.UpdateOutcome;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExperimentRuntimeServiceTest {

    private static final AuditActor ACTOR = AuditActor.user(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Mock
    private ExperimentRepository experimentRepository;

    @Mock
    private ExperimentCommandSupport experimentCommandSupport;

    @Mock
    private ExperimentLifecycleService experimentLifecycleService;

    @Mock
    private AuditService auditService;

    private ExperimentRuntimeService experimentRuntimeService;

    @BeforeEach
    void setUp() {
        experimentRuntimeService = new ExperimentRuntimeService(
                experimentRepository, experimentCommandSupport, experimentLifecycleService, auditService);
        lenient()
                .when(experimentCommandSupport.withExperimentLock(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> action = invocation.getArgument(1);
                    return action.get();
                });
    }

    @Test
    void advanceRollout_shouldAdvanceRunningExperiment() {
        UUID experimentId = UUID.randomUUID();
        Experiment current =
                experiment(experimentId, ExperimentState.RUNNING, ExperimentRolloutPlan.of(5, false, false), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(current.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(current);
        when(experimentRepository.update(any(Experiment.class))).thenReturn(UpdateOutcome.updated(4L));

        Experiment response = experimentRuntimeService.advanceRollout(experimentId, 3L, ACTOR);

        assertThat(response.rolloutPlan().regularRolloutPercentage()).isEqualTo(15);
        assertThat(response.version()).isEqualTo(4L);
        verify(experimentCommandSupport).invalidateCacheAfterCommit(current.flagKey());
        verify(auditService).write(eq(ACTOR), any(), any(), any());
    }

    @Test
    void rollbackRollout_shouldRollbackRunningExperiment() {
        UUID experimentId = UUID.randomUUID();
        Experiment current =
                experiment(experimentId, ExperimentState.RUNNING, ExperimentRolloutPlan.of(15, false, false), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(current.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(current);
        when(experimentRepository.update(any(Experiment.class))).thenReturn(UpdateOutcome.updated(4L));

        Experiment response = experimentRuntimeService.rollbackRollout(experimentId, 3L, ACTOR);

        assertThat(response.rolloutPlan().regularRolloutPercentage()).isEqualTo(5);
        assertThat(response.rolloutPlan().afterRollback()).isTrue();
        verify(experimentCommandSupport).invalidateCacheAfterCommit(current.flagKey());
    }

    @Test
    void advanceRollout_shouldRejectNonRunningExperimentInStrictMode() {
        UUID experimentId = UUID.randomUUID();
        Experiment current = experiment(experimentId, ExperimentState.PAUSED, ExperimentRolloutPlan.initial(), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(current.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(current);

        assertThatThrownBy(() -> experimentRuntimeService.advanceRollout(experimentId, 3L, ACTOR))
                .isInstanceOf(ExperimentRolloutException.class)
                .hasMessage("Cannot advance rollout for experiment in state PAUSED. Allowed source states: [RUNNING]");
    }

    @Test
    void autoAdvanceRollout_shouldIgnoreNonRunningExperiment() {
        UUID experimentId = UUID.randomUUID();
        Experiment current = experiment(experimentId, ExperimentState.PAUSED, ExperimentRolloutPlan.initial(), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(current.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(current);

        experimentRuntimeService.autoAdvanceRollout(experimentId, ACTOR);

        verify(experimentRepository, never()).update(any());
        verify(auditService, never()).write(any(), any(), any(), any());
    }

    @Test
    void autoRollbackRollout_shouldIgnoreExperimentWhenRollbackIsUnavailable() {
        UUID experimentId = UUID.randomUUID();
        Experiment current = experiment(experimentId, ExperimentState.RUNNING, ExperimentRolloutPlan.initial(), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(current.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(current);

        experimentRuntimeService.autoRollbackRollout(experimentId, ACTOR);

        verify(experimentRepository, never()).update(any());
        verify(auditService, never()).write(any(), any(), any(), any());
    }

    @Test
    void rollbackRollout_shouldRejectVersionMismatch() {
        UUID experimentId = UUID.randomUUID();
        Experiment current =
                experiment(experimentId, ExperimentState.RUNNING, ExperimentRolloutPlan.of(15, false, false), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(current.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(current);

        assertThatThrownBy(() -> experimentRuntimeService.rollbackRollout(experimentId, 2L, ACTOR))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage("Experiment '%s' version mismatch. Expected version %d".formatted(experimentId, 2L));
    }

    @Test
    void advanceRollout_shouldRejectUnavailableStepInStrictMode() {
        UUID experimentId = UUID.randomUUID();
        Experiment current =
                experiment(experimentId, ExperimentState.RUNNING, ExperimentRolloutPlan.of(100, false, false), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(current.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(current);

        assertThatThrownBy(() -> experimentRuntimeService.advanceRollout(experimentId, 3L, ACTOR))
                .isInstanceOf(ExperimentRolloutException.class)
                .hasMessage("Cannot advance rollout for experiment %s at step %d".formatted(experimentId, 100));
    }

    @Test
    void advanceRollout_shouldThrowWhenRepositoryReturnsNotFound() {
        UUID experimentId = UUID.randomUUID();
        Experiment current = experiment(experimentId, ExperimentState.RUNNING, ExperimentRolloutPlan.initial(), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(current.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(current);
        when(experimentRepository.update(any(Experiment.class))).thenReturn(UpdateOutcome.notFound());

        assertThatThrownBy(() -> experimentRuntimeService.advanceRollout(experimentId, 3L, ACTOR))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(experimentId));
    }

    @Test
    void rollbackRollout_shouldThrowWhenRepositoryReturnsVersionConflict() {
        UUID experimentId = UUID.randomUUID();
        Experiment current =
                experiment(experimentId, ExperimentState.RUNNING, ExperimentRolloutPlan.of(15, false, false), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(current.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(current);
        when(experimentRepository.update(any(Experiment.class))).thenReturn(UpdateOutcome.versionConflict());

        assertThatThrownBy(() -> experimentRuntimeService.rollbackRollout(experimentId, 3L, ACTOR))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage("Experiment '%s' version mismatch. Expected version %d".formatted(experimentId, 3L));
    }

    @Test
    void pauseOnNegativeAfterRollback_shouldMarkPausedExperimentOnly() {
        UUID experimentId = UUID.randomUUID();
        Experiment pausedExperiment =
                experiment(experimentId, ExperimentState.PAUSED, ExperimentRolloutPlan.of(15, true, false), 3L);
        Experiment markedExperiment =
                pausedExperiment.withRolloutPlan(pausedExperiment.rolloutPlan().markNegativeAfterRollback());
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(pausedExperiment.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(pausedExperiment, pausedExperiment);
        when(experimentRepository.update(any(Experiment.class))).thenReturn(UpdateOutcome.updated(4L));

        experimentRuntimeService.pauseOnNegativeAfterRollback(experimentId, ACTOR);

        ArgumentCaptor<Experiment> experimentCaptor = ArgumentCaptor.forClass(Experiment.class);
        verify(experimentRepository).update(experimentCaptor.capture());
        assertThat(experimentCaptor.getValue().rolloutPlan()).isEqualTo(markedExperiment.rolloutPlan());
        verify(experimentLifecycleService, never()).pause(any(), anyLong());
        verify(auditService, never()).write(any(), any(), any(), any());
    }

    @Test
    void pauseOnNegativeAfterRollback_shouldDoNothingWhenExperimentIsNotRunningOrPaused() {
        UUID experimentId = UUID.randomUUID();
        Experiment completedExperiment =
                experiment(experimentId, ExperimentState.COMPLETED, ExperimentRolloutPlan.of(15, false, false), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(completedExperiment.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(completedExperiment);

        experimentRuntimeService.pauseOnNegativeAfterRollback(experimentId, ACTOR);

        verify(experimentRepository, never()).update(any());
        verify(experimentLifecycleService, never()).pause(any(), anyLong());
        verify(auditService, never()).write(any(), any(), any(), any());
    }

    @Test
    void pauseOnNegativeAfterRollback_shouldPauseAndMarkRunningExperiment() {
        UUID experimentId = UUID.randomUUID();
        Experiment runningExperiment =
                experiment(experimentId, ExperimentState.RUNNING, ExperimentRolloutPlan.of(15, true, false), 3L);
        Experiment pausedExperiment =
                experiment(experimentId, ExperimentState.PAUSED, runningExperiment.rolloutPlan(), 3L);
        Experiment updatedPausedExperiment =
                pausedExperiment.withRolloutPlan(pausedExperiment.rolloutPlan().markNegativeAfterRollback());
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(runningExperiment.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(runningExperiment, pausedExperiment);
        when(experimentLifecycleService.pause(experimentId, 3L)).thenReturn(pausedExperiment);
        when(experimentRepository.update(any(Experiment.class))).thenReturn(UpdateOutcome.updated(4L));

        experimentRuntimeService.pauseOnNegativeAfterRollback(experimentId, ACTOR);

        verify(experimentLifecycleService).pause(experimentId, 3L);
        verify(experimentRepository).update(any(Experiment.class));
        verify(auditService).write(eq(ACTOR), any(), any(), any());

        Object details = ReflectionTestUtils.invokeMethod(
                experimentRuntimeService, "buildAutoPauseDetails", pausedExperiment, updatedPausedExperiment);
        assertThat(details).isNotNull();
    }

    @Test
    void pauseOnNegativeAfterRollback_shouldSkipPersistWhenPausedExperimentIsAlreadyMarkedNegative() {
        UUID experimentId = UUID.randomUUID();
        Experiment pausedExperiment =
                experiment(experimentId, ExperimentState.PAUSED, ExperimentRolloutPlan.of(15, true, true), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(pausedExperiment.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(pausedExperiment, pausedExperiment);

        experimentRuntimeService.pauseOnNegativeAfterRollback(experimentId, ACTOR);

        verify(experimentRepository, never()).update(any());
    }

    @Test
    void buildAutoPauseDetails_shouldOmitReasonWhenExperimentWasOnlyPaused() {
        Experiment pausedExperiment =
                experiment(UUID.randomUUID(), ExperimentState.PAUSED, ExperimentRolloutPlan.of(15, false, false), 3L);
        Experiment updatedExperiment = pausedExperiment.withRolloutPlan(ExperimentRolloutPlan.of(15, false, false));

        Object details = ReflectionTestUtils.invokeMethod(
                experimentRuntimeService, "buildAutoPauseDetails", pausedExperiment, updatedExperiment);

        assertThat(details).isNotNull();
    }

    @Test
    void markNegativeAfterRollback_shouldReturnCurrentExperimentWhenItIsNotPaused() {
        UUID experimentId = UUID.randomUUID();
        Experiment runningExperiment =
                experiment(experimentId, ExperimentState.RUNNING, ExperimentRolloutPlan.of(15, false, false), 3L);
        when(experimentCommandSupport.getFlagKeyById(experimentId)).thenReturn(runningExperiment.flagKey());
        when(experimentCommandSupport.getById(experimentId)).thenReturn(runningExperiment);

        Object response =
                ReflectionTestUtils.invokeMethod(experimentRuntimeService, "markNegativeAfterRollback", experimentId);

        assertThat(response).isEqualTo(runningExperiment);
        verify(experimentRepository, never()).update(any());
    }

    private Experiment experiment(
            UUID experimentId, ExperimentState state, ExperimentRolloutPlan rolloutPlan, long version) {
        return new Experiment(
                experimentId, "flag-orders", "CHECKOUT", rolloutPlan, List.of(), state, version, null, null);
    }
}
