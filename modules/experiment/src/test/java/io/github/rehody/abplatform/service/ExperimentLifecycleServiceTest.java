package io.github.rehody.abplatform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.cache.ExperimentCache;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.exception.ExperimentStateTransitionException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.policy.ExperimentActivationPolicy;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
import io.github.rehody.abplatform.policy.ExperimentTimestampPolicy;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.UpdateOutcome;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ExperimentLifecycleServiceTest {

    @Mock
    private ExperimentRepository experimentRepository;

    @Mock
    private LockExecutor lockExecutor;

    @Mock
    private ExperimentCache experimentCache;

    @Mock
    private ExperimentAssignmentPolicy experimentAssignmentPolicy;

    @Mock
    private ExperimentActivationPolicy experimentActivationPolicy;

    @Mock
    private ExperimentTimestampPolicy experimentTimestampPolicy;

    private ExperimentLifecycleService experimentLifecycleService;

    @BeforeEach
    void setUp() {
        ExperimentCommandSupport experimentCommandSupport = new ExperimentCommandSupport(
                experimentRepository, lockExecutor, new ServiceActionExecutor(), experimentCache);
        experimentLifecycleService = new ExperimentLifecycleService(
                experimentRepository,
                experimentCommandSupport,
                experimentActivationPolicy,
                experimentAssignmentPolicy,
                experimentTimestampPolicy);
        lenient()
                .when(lockExecutor.withLock(any(LockNamespace.class), any(String.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
        lenient()
                .when(experimentTimestampPolicy.applyTransitionTimestamps(
                        any(Experiment.class), any(Experiment.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void submitForReview_shouldUpdateStateIncrementVersionAndInvalidateCache() {
        assertSuccessfulTransition(
                experimentLifecycleService::submitForReview, ExperimentState.DRAFT, ExperimentState.IN_REVIEW);
    }

    @Test
    void approve_shouldUpdateStateIncrementVersionAndInvalidateCache() {
        assertSuccessfulTransition(
                experimentLifecycleService::approve, ExperimentState.IN_REVIEW, ExperimentState.APPROVED);
    }

    @Test
    void reject_shouldUpdateStateIncrementVersionAndInvalidateCache() {
        assertSuccessfulTransition(
                experimentLifecycleService::reject, ExperimentState.IN_REVIEW, ExperimentState.REJECTED);
    }

    @Test
    void start_shouldUpdateStateIncrementVersionAndInvalidateCache() {
        assertSuccessfulTransition(
                experimentLifecycleService::start, ExperimentState.APPROVED, ExperimentState.RUNNING);
    }

    @Test
    void pause_shouldUpdateStateIncrementVersionAndInvalidateCache() {
        assertSuccessfulTransition(experimentLifecycleService::pause, ExperimentState.RUNNING, ExperimentState.PAUSED);
    }

    @Test
    void resume_shouldUpdateStateIncrementVersionAndInvalidateCache() {
        assertSuccessfulTransition(experimentLifecycleService::resume, ExperimentState.PAUSED, ExperimentState.RUNNING);
    }

    @Test
    void complete_shouldUpdateStateIncrementVersionAndInvalidateCache() {
        assertSuccessfulTransition(
                experimentLifecycleService::complete, ExperimentState.PAUSED, ExperimentState.COMPLETED);
    }

    @Test
    void archive_shouldUpdateStateIncrementVersionAndInvalidateCache() {
        assertSuccessfulTransition(
                experimentLifecycleService::archive, ExperimentState.COMPLETED, ExperimentState.ARCHIVED);
    }

    @Test
    void approve_shouldThrowExperimentNotFoundExceptionWhenExperimentMissingBeforeLock() {
        UUID id = UUID.randomUUID();
        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentLifecycleService.approve(id, 3L))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));

        verify(experimentRepository, never()).update(any());
        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void approve_shouldThrowExperimentNotFoundExceptionWhenExperimentMissingAfterLockAcquired() {
        UUID id = UUID.randomUUID();
        String flagKey = "flag-missing";

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of(flagKey));
        when(experimentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentLifecycleService.approve(id, 3L))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));

        verify(experimentRepository, never()).update(any());
        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void approve_shouldThrowOptimisticLockingFailureExceptionWhenExpectedVersionMismatchBeforeUpdate() {
        UUID id = UUID.randomUUID();
        String flagKey = "flag-a";
        Experiment experiment = experiment(id, flagKey, ExperimentState.IN_REVIEW, 4L);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of(flagKey));
        when(experimentRepository.findById(id)).thenReturn(Optional.of(experiment));
        when(experimentRepository.update(any(Experiment.class))).thenReturn(UpdateOutcome.versionConflict());

        assertThatThrownBy(() -> experimentLifecycleService.approve(id, 3L))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage("Experiment '%s' version mismatch. Expected version %d".formatted(id, 3L));

        ArgumentCaptor<Experiment> experimentCaptor = ArgumentCaptor.forClass(Experiment.class);
        verify(experimentRepository).update(experimentCaptor.capture());
        assertThat(experimentCaptor.getValue().version()).isEqualTo(3L);
        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void approve_shouldThrowExperimentStateTransitionExceptionWhenTransitionIsInvalid() {
        UUID id = UUID.randomUUID();
        String flagKey = "flag-b";
        Experiment experiment = experiment(id, flagKey, ExperimentState.DRAFT, 3L);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of(flagKey));
        when(experimentRepository.findById(id)).thenReturn(Optional.of(experiment));

        assertThatThrownBy(() -> experimentLifecycleService.approve(id, 3L))
                .isInstanceOf(ExperimentStateTransitionException.class)
                .hasMessageContaining("Cannot approve experiment in state DRAFT");

        verify(experimentRepository, never()).update(any());
        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void approve_shouldThrowExperimentNotFoundExceptionWhenRepositoryReturnsNotFound() {
        UUID id = UUID.randomUUID();
        String flagKey = "flag-c";
        Experiment experiment = experiment(id, flagKey, ExperimentState.IN_REVIEW, 3L);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of(flagKey));
        when(experimentRepository.findById(id)).thenReturn(Optional.of(experiment));
        when(experimentRepository.update(any(Experiment.class))).thenReturn(UpdateOutcome.notFound());

        assertThatThrownBy(() -> experimentLifecycleService.approve(id, 3L))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));

        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void approve_shouldThrowOptimisticLockingFailureExceptionWhenRepositoryReturnsVersionConflict() {
        UUID id = UUID.randomUUID();
        String flagKey = "flag-d";
        Experiment experiment = experiment(id, flagKey, ExperimentState.IN_REVIEW, 3L);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of(flagKey));
        when(experimentRepository.findById(id)).thenReturn(Optional.of(experiment));
        when(experimentRepository.update(any(Experiment.class))).thenReturn(UpdateOutcome.versionConflict());

        assertThatThrownBy(() -> experimentLifecycleService.approve(id, 3L))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage("Experiment '%s' version mismatch. Expected version %d".formatted(id, 3L));

        verify(experimentCache, never()).invalidate(any());
    }

    private void assertSuccessfulTransition(
            TransitionOperation operation, ExperimentState sourceState, ExperimentState targetState) {
        UUID id = UUID.randomUUID();
        long version = 3L;
        long persistedVersion = 42L;
        String flagKey = "flag-" + targetState.name().toLowerCase();
        Experiment current = experiment(id, flagKey, sourceState, version);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of(flagKey));
        when(experimentRepository.findById(id)).thenReturn(Optional.of(current));
        when(experimentRepository.update(any(Experiment.class))).thenReturn(UpdateOutcome.updated(persistedVersion));

        Experiment response = operation.apply(id, version);

        ArgumentCaptor<Experiment> experimentCaptor = ArgumentCaptor.forClass(Experiment.class);
        ArgumentCaptor<LockNamespace> namespaceCaptor = ArgumentCaptor.forClass(LockNamespace.class);

        verify(experimentRepository).update(experimentCaptor.capture());
        verify(experimentCache).invalidate(flagKey);
        verify(lockExecutor).withLock(namespaceCaptor.capture(), eq(flagKey), any(Supplier.class));

        Experiment updated = experimentCaptor.getValue();
        assertThat(updated.id()).isEqualTo(id);
        assertThat(updated.flagKey()).isEqualTo(flagKey);
        assertThat(updated.variants()).isEqualTo(current.variants());
        assertThat(updated.state()).isEqualTo(targetState);
        assertThat(updated.version()).isEqualTo(version);
        assertThat(namespaceCaptor.getValue().value()).isEqualTo("experiment");

        assertThat(response)
                .isEqualTo(new Experiment(id, flagKey, current.variants(), targetState, persistedVersion, null, null));
    }

    private Experiment experiment(UUID id, String flagKey, ExperimentState state, long version) {
        return new Experiment(id, flagKey, variants(), state, version, null, null);
    }

    private List<ExperimentVariant> variants() {
        return List.of(new ExperimentVariant(
                UUID.randomUUID(),
                "control",
                new FeatureValue(true, FeatureValueType.BOOL),
                0,
                BigDecimal.ONE,
                ExperimentVariantType.CONTROL));
    }

    @FunctionalInterface
    private interface TransitionOperation {
        Experiment apply(UUID id, long version);
    }
}
