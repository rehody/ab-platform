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
import io.github.rehody.abplatform.exception.ExperimentAlreadyExistsException;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
import io.github.rehody.abplatform.policy.ExperimentTimestampPolicy;
import io.github.rehody.abplatform.policy.ExperimentVariantPolicy;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.UpdateOutcome;
import io.github.rehody.abplatform.repository.jdbc.ExperimentDomainJdbcRepository;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ExperimentServiceTest {

    @Mock
    private ExperimentRepository experimentRepository;

    @Mock
    private LockExecutor lockExecutor;

    @Mock
    private ExperimentCache experimentCache;

    @Mock
    private ExperimentAssignmentPolicy experimentAssignmentPolicy;

    @Mock
    private ExperimentTimestampPolicy experimentTimestampPolicy;

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private ExperimentVariantPolicy experimentVariantPolicy;

    @Mock
    private ExperimentDomainJdbcRepository experimentDomainJdbcRepository;

    private ExperimentService experimentService;

    @BeforeEach
    void setUp() {
        ServiceActionExecutor serviceActionExecutor = new ServiceActionExecutor();
        ExperimentCommandSupport experimentCommandSupport = new ExperimentCommandSupport(
                experimentRepository, lockExecutor, serviceActionExecutor, experimentCache);
        experimentService = new ExperimentService(
                experimentRepository,
                experimentCommandSupport,
                experimentCache,
                experimentAssignmentPolicy,
                experimentTimestampPolicy,
                featureFlagService,
                experimentVariantPolicy,
                experimentDomainJdbcRepository);
        lenient()
                .when(lockExecutor.withLock(any(LockNamespace.class), any(String.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
        lenient()
                .when(experimentTimestampPolicy.initializeTimestamps(any(Experiment.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(featureFlagService.getByKey(any()))
                .thenAnswer(invocation -> new FeatureFlag(
                        UUID.randomUUID(),
                        invocation.getArgument(0),
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0L));
        lenient().when(experimentDomainJdbcRepository.existsByKey(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void create_shouldThrowExperimentAlreadyExistsExceptionAndSkipSaveWhenFlagKeyExists() {
        List<ExperimentVariant> variants = variants();
        when(experimentRepository.existsByFlagKey("flag-a")).thenReturn(true);

        assertThatThrownBy(() -> experimentService.create("flag-a", "CHECKOUT", variants, ExperimentState.DRAFT))
                .isInstanceOf(ExperimentAlreadyExistsException.class)
                .hasMessage("Experiment with flag key 'flag-a' already exists");

        verify(experimentRepository, never()).save(any());
        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void create_shouldSaveExperimentAndInvalidateCacheWhenSynchronizationInactive() {
        List<ExperimentVariant> variants = variants();
        when(experimentRepository.existsByFlagKey("flag-b")).thenReturn(false);

        Experiment response = experimentService.create("flag-b", "CHECKOUT", variants, ExperimentState.APPROVED);

        ArgumentCaptor<Experiment> experimentCaptor = ArgumentCaptor.forClass(Experiment.class);
        ArgumentCaptor<LockNamespace> namespaceCaptor = ArgumentCaptor.forClass(LockNamespace.class);

        verify(experimentRepository).save(experimentCaptor.capture());
        verify(experimentCache).invalidate("flag-b");
        verify(lockExecutor).withLock(namespaceCaptor.capture(), eq("flag-b"), any(Supplier.class));

        Experiment savedExperiment = experimentCaptor.getValue();
        assertThat(savedExperiment.id()).isNotNull();
        assertThat(savedExperiment.flagKey()).isEqualTo("flag-b");
        assertThat(savedExperiment.domainKey()).isEqualTo("CHECKOUT");
        assertThat(savedExperiment.variants()).isEqualTo(variants);
        assertThat(savedExperiment.state()).isEqualTo(ExperimentState.APPROVED);
        assertThat(savedExperiment.version()).isZero();
        assertThat(namespaceCaptor.getValue().value()).isEqualTo("experiment");

        assertThat(response.flagKey()).isEqualTo("flag-b");
        assertThat(response.domainKey()).isEqualTo("CHECKOUT");
        assertThat(response.variants()).isEqualTo(variants);
        assertThat(response.state()).isEqualTo(ExperimentState.APPROVED);
        assertThat(response.version()).isZero();
    }

    @Test
    void create_shouldRegisterAfterCommitActionAndInvalidateCacheAfterCommitWhenSynchronizationActive() {
        List<ExperimentVariant> variants = variants();
        when(experimentRepository.existsByFlagKey("flag-c")).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();

        Experiment response = experimentService.create("flag-c", "PRICING", variants, ExperimentState.RUNNING);

        verify(experimentCache, never()).invalidate("flag-c");
        assertThat(response.flagKey()).isEqualTo("flag-c");
        assertThat(response.domainKey()).isEqualTo("PRICING");

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(experimentCache).invalidate("flag-c");
    }

    @Test
    void update_shouldUseCurrentFlagKeyForPartialUpdateInvalidateCacheAndReturnUpdatedResponse() {
        UUID id = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        Experiment current =
                new Experiment(id, "flag-d", "CHECKOUT", variants, ExperimentState.RUNNING, 3L, null, null);
        when(experimentRepository.findById(id)).thenReturn(Optional.of(current));
        when(experimentRepository.findByFlagKey("flag-d")).thenReturn(Optional.of(current));
        when(experimentRepository.updateWithVariants(any(Experiment.class))).thenReturn(UpdateOutcome.updated(4L));

        Experiment response = experimentService.update(id, null, "PRICING", variants, 3L);

        ArgumentCaptor<Experiment> experimentCaptor = ArgumentCaptor.forClass(Experiment.class);
        verify(experimentRepository).updateWithVariants(experimentCaptor.capture());
        verify(experimentCache).invalidate("flag-d");
        Experiment updatedExperiment = experimentCaptor.getValue();
        assertThat(updatedExperiment.flagKey()).isEqualTo("flag-d");
        assertThat(updatedExperiment.domainKey()).isEqualTo("PRICING");
        assertThat(updatedExperiment.version()).isEqualTo(3L);
        assertThat(response.flagKey()).isEqualTo("flag-d");
        assertThat(response.domainKey()).isEqualTo("PRICING");
        assertThat(response.variants()).isEqualTo(variants);
        assertThat(response.state()).isEqualTo(ExperimentState.RUNNING);
        assertThat(response.version()).isEqualTo(4L);
    }

    @Test
    void update_shouldThrowExperimentNotFoundExceptionWhenExperimentMissingBeforeLock() {
        UUID id = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        when(experimentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.update(id, "flag-d", "CHECKOUT", variants, 2L))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));

        verify(experimentRepository, never()).updateWithVariants(any());
        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void update_shouldThrowExperimentNotFoundExceptionWhenRepositoryReturnsNotFound() {
        UUID id = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        Experiment current =
                new Experiment(id, "flag-e", "CHECKOUT", variants, ExperimentState.RUNNING, 2L, null, null);
        when(experimentRepository.findById(id)).thenReturn(Optional.of(current));
        when(experimentRepository.findByFlagKey("flag-e")).thenReturn(Optional.of(current));
        when(experimentRepository.updateWithVariants(any(Experiment.class))).thenReturn(UpdateOutcome.notFound());

        assertThatThrownBy(() -> experimentService.update(id, "flag-e", "CHECKOUT", variants, 2L))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));

        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void update_shouldThrowOptimisticLockingFailureExceptionWhenVersionMismatch() {
        UUID id = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        Experiment current =
                new Experiment(id, "flag-f", "CHECKOUT", variants, ExperimentState.RUNNING, 2L, null, null);
        when(experimentRepository.findById(id)).thenReturn(Optional.of(current));
        when(experimentRepository.findByFlagKey("flag-f")).thenReturn(Optional.of(current));
        when(experimentRepository.updateWithVariants(any(Experiment.class)))
                .thenReturn(UpdateOutcome.versionConflict());

        assertThatThrownBy(() -> experimentService.update(id, "flag-f", "CHECKOUT", variants, 2L))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage("Experiment '%s' version mismatch. Expected version %d".formatted(id, 2L));

        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void update_shouldThrowExperimentAlreadyExistsExceptionWhenAnotherExperimentAlreadyUsesNewFlagKey() {
        UUID id = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        Experiment current =
                new Experiment(id, "flag-g", "CHECKOUT", variants, ExperimentState.RUNNING, 2L, null, null);
        Experiment conflicting = new Experiment(
                UUID.randomUUID(), "flag-new", "PRICING", variants, ExperimentState.DRAFT, 1L, null, null);
        when(experimentRepository.findById(id)).thenReturn(Optional.of(current));
        when(experimentRepository.findByFlagKey("flag-new")).thenReturn(Optional.of(conflicting));

        assertThatThrownBy(() -> experimentService.update(id, "flag-new", "CHECKOUT", variants, 2L))
                .isInstanceOf(ExperimentAlreadyExistsException.class)
                .hasMessage("Experiment with flag key 'flag-new' already exists");

        verify(experimentRepository, never()).updateWithVariants(any());
        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void getById_shouldLoadFromRepositoryAndReturnResponseWhenCacheNeedsLoader() {
        UUID id = UUID.randomUUID();
        Experiment persisted =
                new Experiment(id, "flag-g", "CHECKOUT", variants(), ExperimentState.PAUSED, 8L, null, null);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-g"));
        when(experimentRepository.findByFlagKey("flag-g")).thenReturn(Optional.of(persisted));
        when(experimentCache.getOrLoad(eq("flag-g"), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<Optional<Experiment>> loader = invocation.getArgument(1);
            return loader.get();
        });

        Experiment response = experimentService.getById(id);

        assertThat(response.flagKey()).isEqualTo("flag-g");
        assertThat(response.domainKey()).isEqualTo("CHECKOUT");
        assertThat(response.variants()).isEqualTo(persisted.variants());
        assertThat(response.state()).isEqualTo(ExperimentState.PAUSED);
        assertThat(response.version()).isEqualTo(8L);
        verify(experimentRepository).findByFlagKey("flag-g");
    }

    @Test
    void getById_shouldReturnCachedResponseAndSkipRepositoryLookupByFlagKeyWhenCacheHit() {
        UUID id = UUID.randomUUID();
        Experiment experiment =
                new Experiment(id, "flag-h", "CHECKOUT", variants(), ExperimentState.DRAFT, 12L, null, null);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-h"));
        when(experimentCache.getOrLoad(eq("flag-h"), any(Supplier.class))).thenReturn(Optional.of(experiment));

        Experiment response = experimentService.getById(id);

        assertThat(response).isEqualTo(experiment);
        verify(experimentRepository, never()).findByFlagKey(any());
    }

    @Test
    void getById_shouldThrowExperimentNotFoundExceptionWhenCacheAndRepositoryMiss() {
        UUID id = UUID.randomUUID();
        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-i"));
        when(experimentCache.getOrLoad(eq("flag-i"), any(Supplier.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.getById(id))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));
    }

    @Test
    void getAll_shouldReturnMappedResponses() {
        Experiment first = new Experiment(
                UUID.randomUUID(), "flag-j", "CHECKOUT", variants(), ExperimentState.DRAFT, 1L, null, null);
        Experiment second = new Experiment(
                UUID.randomUUID(), "flag-k", "PRICING", variants(), ExperimentState.ARCHIVED, 2L, null, null);
        when(experimentRepository.findAll()).thenReturn(List.of(first, second));

        List<Experiment> responses = experimentService.getAll();

        assertThat(responses).containsExactly(first, second);
    }

    @Test
    void getRunning_shouldReturnOnlyRunningExperimentsFromRepository() {
        Experiment first = new Experiment(
                UUID.randomUUID(), "flag-running-a", "CHECKOUT", variants(), ExperimentState.RUNNING, 3L, null, null);
        Experiment second = new Experiment(
                UUID.randomUUID(), "flag-running-b", "PRICING", variants(), ExperimentState.RUNNING, 4L, null, null);
        when(experimentRepository.findRunning()).thenReturn(List.of(first, second));

        List<Experiment> responses = experimentService.getRunning();

        assertThat(responses).containsExactly(first, second);
    }

    @Test
    void ensureExistsById_shouldDoNothingWhenExperimentExists() {
        UUID id = UUID.randomUUID();
        when(experimentRepository.existsById(id)).thenReturn(true);

        experimentService.ensureExistsById(id);
    }

    @Test
    void ensureExistsById_shouldThrowWhenExperimentDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(experimentRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> experimentService.ensureExistsById(id))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));
    }

    private List<ExperimentVariant> variants() {
        return List.of(
                new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE,
                        ExperimentVariantType.CONTROL),
                new ExperimentVariant(
                        UUID.randomUUID(),
                        "variant-a",
                        new FeatureValue(false, FeatureValueType.BOOL),
                        1,
                        BigDecimal.ONE,
                        ExperimentVariantType.REGULAR));
    }
}
