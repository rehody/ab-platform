package io.github.rehody.abplatform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.cache.CachedExperiment;
import io.github.rehody.abplatform.cache.ExperimentCache;
import io.github.rehody.abplatform.dto.request.ExperimentCreateRequest;
import io.github.rehody.abplatform.dto.request.ExperimentUpdateRequest;
import io.github.rehody.abplatform.dto.response.ExperimentResponse;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.exception.ExperimentAlreadyExistsException;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.ReplaceVariantsResult;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
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

    private ServiceActionExecutor serviceActionExecutor;
    private ExperimentService experimentService;

    @BeforeEach
    void setUp() {
        serviceActionExecutor = new ServiceActionExecutor();
        experimentService =
                new ExperimentService(experimentRepository, lockExecutor, serviceActionExecutor, experimentCache);
        lenient()
                .when(lockExecutor.withLock(any(LockNamespace.class), any(String.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void create_shouldThrowExperimentAlreadyExistsExceptionAndSkipSaveWhenFlagKeyExists() {
        ExperimentCreateRequest request = new ExperimentCreateRequest("flag-a", variants(), ExperimentState.DRAFT);
        when(experimentRepository.existsByFlagKey("flag-a")).thenReturn(true);

        assertThatThrownBy(() -> experimentService.create(request))
                .isInstanceOf(ExperimentAlreadyExistsException.class)
                .hasMessage("Experiment with flag key 'flag-a' already exists");

        verify(experimentRepository, never()).save(any());
        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void create_shouldSaveExperimentAndInvalidateCacheWhenSynchronizationInactive() {
        ExperimentCreateRequest request = new ExperimentCreateRequest("flag-b", variants(), ExperimentState.APPROVED);
        when(experimentRepository.existsByFlagKey("flag-b")).thenReturn(false);

        ExperimentResponse response = experimentService.create(request);

        ArgumentCaptor<Experiment> experimentCaptor = ArgumentCaptor.forClass(Experiment.class);
        ArgumentCaptor<LockNamespace> namespaceCaptor = ArgumentCaptor.forClass(LockNamespace.class);

        verify(experimentRepository).save(experimentCaptor.capture());
        verify(experimentCache).invalidate("flag-b");
        verify(lockExecutor).withLock(namespaceCaptor.capture(), eq("flag-b"), any(Supplier.class));

        Experiment savedExperiment = experimentCaptor.getValue();
        assertThat(savedExperiment.id()).isNotNull();
        assertThat(savedExperiment.flagKey()).isEqualTo("flag-b");
        assertThat(savedExperiment.variants()).isEqualTo(request.variants());
        assertThat(savedExperiment.state()).isEqualTo(ExperimentState.APPROVED);
        assertThat(savedExperiment.version()).isZero();
        assertThat(namespaceCaptor.getValue().value()).isEqualTo("experiment");

        assertThat(response.flagKey()).isEqualTo("flag-b");
        assertThat(response.variants()).isEqualTo(request.variants());
        assertThat(response.state()).isEqualTo(ExperimentState.APPROVED);
        assertThat(response.version()).isZero();
    }

    @Test
    void create_shouldRegisterAfterCommitActionAndInvalidateCacheAfterCommitWhenSynchronizationActive() {
        ExperimentCreateRequest request = new ExperimentCreateRequest("flag-c", variants(), ExperimentState.RUNNING);
        when(experimentRepository.existsByFlagKey("flag-c")).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();

        ExperimentResponse response = experimentService.create(request);

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
        ExperimentUpdateRequest request = new ExperimentUpdateRequest(variants(), 3L);
        Experiment updated = new Experiment(id, "flag-d", variants(), ExperimentState.RUNNING, 4L);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-d"));
        when(experimentRepository.replaceVariants(id, 3L, request.variants()))
                .thenReturn(ReplaceVariantsResult.UPDATED);
        when(experimentRepository.findById(id)).thenReturn(Optional.of(updated));

        ExperimentResponse response = experimentService.update(id, request);

        verify(experimentRepository).replaceVariants(id, 3L, request.variants());
        verify(experimentCache).invalidate("flag-d");
        assertThat(response.flagKey()).isEqualTo("flag-d");
        assertThat(response.variants()).isEqualTo(updated.variants());
        assertThat(response.state()).isEqualTo(ExperimentState.RUNNING);
        assertThat(response.version()).isEqualTo(4L);
    }

    @Test
    void update_shouldThrowExperimentNotFoundExceptionWhenExperimentMissingBeforeLock() {
        UUID id = UUID.randomUUID();
        ExperimentUpdateRequest request = new ExperimentUpdateRequest(variants(), 2L);
        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.update(id, request))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));

        verify(experimentRepository, never()).replaceVariants(any(), any(Long.class), any());
        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void update_shouldThrowExperimentNotFoundExceptionWhenRepositoryReturnsNotFound() {
        UUID id = UUID.randomUUID();
        ExperimentUpdateRequest request = new ExperimentUpdateRequest(variants(), 2L);
        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-e"));
        when(experimentRepository.replaceVariants(id, 2L, request.variants()))
                .thenReturn(ReplaceVariantsResult.NOT_FOUND);

        assertThatThrownBy(() -> experimentService.update(id, request))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));

        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void update_shouldThrowOptimisticLockingFailureExceptionWhenVersionMismatch() {
        UUID id = UUID.randomUUID();
        ExperimentUpdateRequest request = new ExperimentUpdateRequest(variants(), 2L);
        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-f"));
        when(experimentRepository.replaceVariants(id, 2L, request.variants()))
                .thenReturn(ReplaceVariantsResult.VERSION_CONFLICT);

        assertThatThrownBy(() -> experimentService.update(id, request))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage("Experiment '%s' version mismatch. Expected version %d".formatted(id, 2L));

        verify(experimentCache, never()).invalidate(any());
    }

    @Test
    void update_shouldThrowExperimentNotFoundExceptionWhenUpdatedExperimentCannotBeReadBack() {
        UUID id = UUID.randomUUID();
        ExperimentUpdateRequest request = new ExperimentUpdateRequest(variants(), 2L);
        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-g"));
        when(experimentRepository.replaceVariants(id, 2L, request.variants()))
                .thenReturn(ReplaceVariantsResult.UPDATED);
        when(experimentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.update(id, request))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessage("Experiment '%s' not found".formatted(id));

        verify(experimentCache).invalidate("flag-g");
    }

    @Test
    void getById_shouldLoadFromRepositoryAndReturnResponseWhenCacheNeedsLoader() {
        UUID id = UUID.randomUUID();
        Experiment persisted = new Experiment(id, "flag-g", variants(), ExperimentState.PAUSED, 8L);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-g"));
        when(experimentRepository.findByFlagKey("flag-g")).thenReturn(Optional.of(persisted));
        when(experimentCache.getOrLoad(eq("flag-g"), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<Optional<CachedExperiment>> loader = invocation.getArgument(1);
            return loader.get();
        });

        ExperimentResponse response = experimentService.getById(id);

        assertThat(response.flagKey()).isEqualTo("flag-g");
        assertThat(response.variants()).isEqualTo(persisted.variants());
        assertThat(response.state()).isEqualTo(ExperimentState.PAUSED);
        assertThat(response.version()).isEqualTo(8L);
        verify(experimentRepository).findByFlagKey("flag-g");
    }

    @Test
    void getById_shouldReturnCachedResponseAndSkipRepositoryLookupByFlagKeyWhenCacheHit() {
        UUID id = UUID.randomUUID();
        CachedExperiment cachedExperiment = new CachedExperiment(id, "flag-h", variants(), ExperimentState.DRAFT, 12L);

        when(experimentRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-h"));
        when(experimentCache.getOrLoad(eq("flag-h"), any(Supplier.class))).thenReturn(Optional.of(cachedExperiment));

        ExperimentResponse response = experimentService.getById(id);

        assertThat(response).isEqualTo(cachedExperiment.toResponse());
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
        Experiment first = new Experiment(UUID.randomUUID(), "flag-j", variants(), ExperimentState.DRAFT, 1L);
        Experiment second = new Experiment(UUID.randomUUID(), "flag-k", variants(), ExperimentState.ARCHIVED, 2L);
        when(experimentRepository.findAll()).thenReturn(List.of(first, second));

        List<ExperimentResponse> responses = experimentService.getAll();

        assertThat(responses).containsExactly(ExperimentResponse.from(first), ExperimentResponse.from(second));
    }

    private List<ExperimentVariant> variants() {
        return List.of(
                new ExperimentVariant(UUID.randomUUID(), "control", new FeatureValue(true, FeatureValueType.BOOL), 0));
    }
}
