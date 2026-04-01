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
import io.github.rehody.abplatform.repository.ExperimentRepository.ReplaceVariantsResult;
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

    private ServiceActionExecutor serviceActionExecutor;
    private ExperimentCommandSupport experimentCommandSupport;
    private ExperimentService experimentService;

    @BeforeEach
    void setUp() {
        serviceActionExecutor = new ServiceActionExecutor();
        experimentCommandSupport = new ExperimentCommandSupport(
                experimentRepository, lockExecutor, serviceActionExecutor, experimentCache);
        experimentService = new ExperimentService(
                experimentRepository,
                experimentCommandSupport,
                experimentCache,
                experimentAssignmentPolicy,
                experimentTimestampPolicy,
                featureFlagService,
                experimentVariantPolicy);
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

        assertThatThrownBy(() -> experimentService.create("flag-a", variants, ExperimentState.DRAFT))
                .isInstanceOf(ExperimentAlreadyExistsException.class)
                .hasMessage("Experiment with flag key 'flag-a' already exists");

        verify(experimentRepository, never()).save(any());
        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void create_shouldSaveExperimentAndInvalidateCacheWhenSynchronizationInactive() {
        List<ExperimentVariant> variants = variants();
        when(experimentRepository.existsByFlagKey("flag-b")).thenReturn(false);

        Experiment response = experimentService.create("flag-b", variants, ExperimentState.APPROVED);

        ArgumentCaptor<Experiment> experimentCaptor = ArgumentCaptor.forClass(Experiment.class);
        ArgumentCaptor<LockNamespace> namespaceCaptor = ArgumentCaptor.forClass(LockNamespace.class);

        verify(experimentRepository).save(experimentCaptor.capture());
        verify(experimentCache).invalidate("flag-b");
        verify(lockExecutor).withLock(namespaceCaptor.capture(), eq("flag-b"), any(Supplier.class));

        Experiment savedExperiment = experimentCaptor.getValue();
        assertThat(savedExperiment.id()).isNotNull();
        assertThat(savedExperiment.flagKey()).isEqualTo("flag-b");
        assertThat(savedExperiment.variants()).isEqualTo(variants);
        assertThat(savedExperiment.state()).isEqualTo(ExperimentState.APPROVED);
        assertThat(savedExperiment.version()).isZero();
        assertThat(namespaceCaptor.getValue().value()).isEqualTo("experiment");

        assertThat(response.flagKey()).isEqualTo("flag-b");
        assertThat(response.variants()).isEqualTo(variants);
        assertThat(response.state()).isEqualTo(ExperimentState.APPROVED);
        assertThat(response.version()).isZero();
    }

    @Test
    void create_shouldRegisterAfterCommitActionAndInvalidateCacheAfterCommitWhenSynchronizationActive() {
        List<ExperimentVariant> variants = variants();
        when(experimentRepository.existsByFlagKey("flag-c")).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();

        Experiment response = experimentService.create("flag-c", variants, ExperimentState.RUNNING);

        verify(experimentCache, never()).invalidate("flag-c");
        assertThat(response.flagKey()).isEqualTo("flag-c");

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(experimentCache).invalidate("flag-c");
    }

    @Test
    void update_shouldReplaceVariantsInvalidateCacheAndReturnUpdatedResponse() {
        UUID id = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        Experiment updated = new Experiment(id, "flag-d", variants, ExperimentState.RUNNING, 4L, null, null);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-d"));
        when(experimentRepository.replaceVariants(id, 3L, variants)).thenReturn(ReplaceVariantsResult.UPDATED);
        when(experimentRepository.findById(id)).thenReturn(Optional.of(updated));

        Experiment response = experimentService.update(id, variants, 3L);

        verify(experimentRepository).replaceVariants(id, 3L, variants);
        verify(experimentCache).invalidate("flag-d");
        assertThat(response.flagKey()).isEqualTo("flag-d");
        assertThat(response.variants()).isEqualTo(updated.variants());
        assertThat(response.state()).isEqualTo(ExperimentState.RUNNING);
        assertThat(response.version()).isEqualTo(4L);
    }

    @Test
    void update_shouldThrowExperimentNotFoundExceptionWhenExperimentMissingBeforeLock() {
        UUID id = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.update(id, variants, 2L))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));

        verify(experimentRepository, never()).replaceVariants(any(), any(Long.class), any());
        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void update_shouldThrowExperimentNotFoundExceptionWhenRepositoryReturnsNotFound() {
        UUID id = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        Experiment current = new Experiment(id, "flag-e", variants, ExperimentState.RUNNING, 2L, null, null);
        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-e"));
        when(experimentRepository.findById(id)).thenReturn(Optional.of(current));
        when(experimentRepository.replaceVariants(id, 2L, variants)).thenReturn(ReplaceVariantsResult.NOT_FOUND);

        assertThatThrownBy(() -> experimentService.update(id, variants, 2L))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));

        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void update_shouldThrowOptimisticLockingFailureExceptionWhenVersionMismatch() {
        UUID id = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        Experiment current = new Experiment(id, "flag-f", variants, ExperimentState.RUNNING, 2L, null, null);
        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-f"));
        when(experimentRepository.findById(id)).thenReturn(Optional.of(current));
        when(experimentRepository.replaceVariants(id, 2L, variants)).thenReturn(ReplaceVariantsResult.VERSION_CONFLICT);

        assertThatThrownBy(() -> experimentService.update(id, variants, 2L))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage("Experiment '%s' version mismatch. Expected version %d".formatted(id, 2L));

        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void update_shouldThrowExperimentNotFoundExceptionWhenUpdatedExperimentCannotBeReadBack() {
        UUID id = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        Experiment current = new Experiment(id, "flag-g", variants, ExperimentState.RUNNING, 2L, null, null);
        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-g"));
        when(experimentRepository.replaceVariants(id, 2L, variants)).thenReturn(ReplaceVariantsResult.UPDATED);
        when(experimentRepository.findById(id)).thenReturn(Optional.of(current), Optional.empty());

        assertThatThrownBy(() -> experimentService.update(id, variants, 2L))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));

        verify(experimentCache).invalidate("flag-g");
    }

    @Test
    void getById_shouldLoadFromRepositoryAndReturnResponseWhenCacheNeedsLoader() {
        UUID id = UUID.randomUUID();
        Experiment persisted = new Experiment(id, "flag-g", variants(), ExperimentState.PAUSED, 8L, null, null);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-g"));
        when(experimentRepository.findByFlagKey("flag-g")).thenReturn(Optional.of(persisted));
        when(experimentCache.getOrLoad(eq("flag-g"), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<Optional<Experiment>> loader = invocation.getArgument(1);
            return loader.get();
        });

        Experiment response = experimentService.getById(id);

        assertThat(response.flagKey()).isEqualTo("flag-g");
        assertThat(response.variants()).isEqualTo(persisted.variants());
        assertThat(response.state()).isEqualTo(ExperimentState.PAUSED);
        assertThat(response.version()).isEqualTo(8L);
        verify(experimentRepository).findByFlagKey("flag-g");
    }

    @Test
    void getById_shouldReturnCachedResponseAndSkipRepositoryLookupByFlagKeyWhenCacheHit() {
        UUID id = UUID.randomUUID();
        Experiment experiment = new Experiment(id, "flag-h", variants(), ExperimentState.DRAFT, 12L, null, null);

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
        Experiment first =
                new Experiment(UUID.randomUUID(), "flag-j", variants(), ExperimentState.DRAFT, 1L, null, null);
        Experiment second =
                new Experiment(UUID.randomUUID(), "flag-k", variants(), ExperimentState.ARCHIVED, 2L, null, null);
        when(experimentRepository.findAll()).thenReturn(List.of(first, second));

        List<Experiment> responses = experimentService.getAll();

        assertThat(responses).containsExactly(first, second);
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
