package io.github.rehody.abplatform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.cache.FeatureFlagCache;
import io.github.rehody.abplatform.dto.request.FeatureFlagCreateRequest;
import io.github.rehody.abplatform.dto.request.FeatureFlagUpdateRequest;
import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.exception.FeatureFlagAlreadyExistsException;
import io.github.rehody.abplatform.exception.FeatureFlagNotFoundException;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.repository.FeatureFlagRepository;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
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
class FeatureFlagServiceTest {

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @Mock
    private LockExecutor lockExecutor;

    @Mock
    private FeatureFlagCache featureFlagCache;

    private FeatureFlagService featureFlagService;

    @BeforeEach
    void setUp() {
        featureFlagService = new FeatureFlagService(featureFlagRepository, lockExecutor, featureFlagCache);
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
    void create_shouldThrowFeatureFlagAlreadyExistsExceptionAndSkipSaveWhenKeyExists() {
        FeatureFlagCreateRequest request =
                new FeatureFlagCreateRequest("flag-a", new FeatureValue(true, FeatureValueType.BOOL));
        when(featureFlagRepository.existsByKey("flag-a")).thenReturn(true);

        assertThatThrownBy(() -> featureFlagService.create(request))
                .isInstanceOf(FeatureFlagAlreadyExistsException.class)
                .hasMessage("Feature flag 'flag-a' already exists");

        verify(featureFlagRepository, never()).save(any());
        verify(featureFlagCache, never()).invalidate(any());
    }

    @Test
    void create_shouldSaveFeatureFlagAndInvalidateCacheWhenSynchronizationInactive() {
        FeatureFlagCreateRequest request =
                new FeatureFlagCreateRequest("flag-b", new FeatureValue("v1", FeatureValueType.STRING));
        when(featureFlagRepository.existsByKey("flag-b")).thenReturn(false);

        FeatureFlagResponse response = featureFlagService.create(request);

        ArgumentCaptor<FeatureFlag> featureFlagCaptor = ArgumentCaptor.forClass(FeatureFlag.class);
        ArgumentCaptor<LockNamespace> namespaceCaptor = ArgumentCaptor.forClass(LockNamespace.class);

        verify(featureFlagRepository).save(featureFlagCaptor.capture());
        verify(featureFlagCache).invalidate("flag-b");
        verify(lockExecutor).withLock(namespaceCaptor.capture(), eq("flag-b"), any(Supplier.class));

        FeatureFlag savedFeatureFlag = featureFlagCaptor.getValue();
        assertThat(savedFeatureFlag.id()).isNotNull();
        assertThat(savedFeatureFlag.key()).isEqualTo("flag-b");
        assertThat(savedFeatureFlag.defaultValue()).isEqualTo(request.defaultValue());
        assertThat(savedFeatureFlag.version()).isZero();
        assertThat(namespaceCaptor.getValue().value()).isEqualTo("feature-flag");

        assertThat(response.key()).isEqualTo("flag-b");
        assertThat(response.defaultValue()).isEqualTo(request.defaultValue());
        assertThat(response.version()).isZero();
    }

    @Test
    void create_shouldRegisterAfterCommitActionAndInvalidateCacheAfterCommitWhenSynchronizationActive() {
        FeatureFlagCreateRequest request =
                new FeatureFlagCreateRequest("flag-c", new FeatureValue(10, FeatureValueType.NUMBER));
        when(featureFlagRepository.existsByKey("flag-c")).thenReturn(false);

        TransactionSynchronizationManager.initSynchronization();

        FeatureFlagResponse response = featureFlagService.create(request);

        verify(featureFlagCache, never()).invalidate("flag-c");
        assertThat(response.key()).isEqualTo("flag-c");

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(featureFlagCache).invalidate("flag-c");
    }

    @Test
    void update_shouldUpdateAndReturnFeatureFlagResponseWhenFeatureFlagExists() {
        FeatureValue defaultValue = new FeatureValue(false, FeatureValueType.BOOL);
        FeatureFlagUpdateRequest request = new FeatureFlagUpdateRequest(defaultValue, 3L);
        FeatureFlag persisted = new FeatureFlag(UUID.randomUUID(), "flag-d", defaultValue, 4L);

        when(featureFlagRepository.update("flag-d", defaultValue, 3L)).thenReturn(1);
        when(featureFlagRepository.findByKey("flag-d")).thenReturn(Optional.of(persisted));

        FeatureFlagResponse response = featureFlagService.update("flag-d", request);

        verify(featureFlagRepository).update("flag-d", defaultValue, 3L);
        verify(featureFlagCache).invalidate("flag-d");
        assertThat(response.key()).isEqualTo("flag-d");
        assertThat(response.defaultValue()).isEqualTo(defaultValue);
        assertThat(response.version()).isEqualTo(4L);
    }

    @Test
    void update_shouldThrowFeatureFlagNotFoundExceptionAndSkipCacheInvalidationWhenFeatureFlagMissing() {
        FeatureValue defaultValue = new FeatureValue(false, FeatureValueType.BOOL);
        FeatureFlagUpdateRequest request = new FeatureFlagUpdateRequest(defaultValue, 5L);

        when(featureFlagRepository.existsByKey("flag-e")).thenReturn(false);

        assertThatThrownBy(() -> featureFlagService.update("flag-e", request))
                .isInstanceOf(FeatureFlagNotFoundException.class)
                .hasMessage("Feature flag 'flag-e' not found");

        verify(featureFlagRepository).update("flag-e", defaultValue, 5L);
        verify(featureFlagCache, never()).invalidate("flag-e");
    }

    @Test
    void update_shouldThrowOptimisticLockingFailureExceptionWhenVersionMismatch() {
        FeatureValue defaultValue = new FeatureValue(false, FeatureValueType.BOOL);
        FeatureFlagUpdateRequest request = new FeatureFlagUpdateRequest(defaultValue, 2L);

        when(featureFlagRepository.existsByKey("flag-e")).thenReturn(true);
        when(featureFlagRepository.update("flag-e", defaultValue, 2L)).thenReturn(0);

        assertThatThrownBy(() -> featureFlagService.update("flag-e", request))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage("Feature flag 'flag-e' version mismatch. Expected version 2");

        verify(featureFlagRepository).update("flag-e", defaultValue, 2L);
        verify(featureFlagCache, never()).invalidate("flag-e");
    }

    @Test
    void getByKey_shouldLoadFromRepositoryAndReturnResponseWhenCacheNeedsLoader() {
        String key = "flag-f";
        FeatureFlag persisted =
                new FeatureFlag(UUID.randomUUID(), key, new FeatureValue("enabled", FeatureValueType.STRING), 8L);

        when(featureFlagRepository.findByKey(key)).thenReturn(Optional.of(persisted));
        when(featureFlagCache.getOrLoad(eq(key), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<Optional<FeatureFlagResponse>> loader = invocation.getArgument(1);
            return loader.get();
        });

        FeatureFlagResponse response = featureFlagService.getByKey(key);

        assertThat(response.key()).isEqualTo(key);
        assertThat(response.defaultValue()).isEqualTo(persisted.defaultValue());
        assertThat(response.version()).isEqualTo(8L);
        verify(featureFlagRepository).findByKey(key);
    }

    @Test
    void getByKey_shouldReturnCachedResponseAndSkipRepositoryWhenCacheHit() {
        String key = "flag-g";
        FeatureFlagResponse cachedResponse =
                new FeatureFlagResponse(key, new FeatureValue(true, FeatureValueType.BOOL), 12L);

        when(featureFlagCache.getOrLoad(eq(key), any(Supplier.class))).thenReturn(Optional.of(cachedResponse));

        FeatureFlagResponse response = featureFlagService.getByKey(key);

        assertThat(response).isEqualTo(cachedResponse);
        verify(featureFlagRepository, never()).findByKey(any());
    }

    @Test
    void getByKey_shouldThrowFeatureFlagNotFoundExceptionAndPropagateMissingFlagWhenCacheAndRepositoryMiss() {
        String key = "flag-h";

        when(featureFlagCache.getOrLoad(eq(key), any(Supplier.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> featureFlagService.getByKey(key))
                .isInstanceOf(FeatureFlagNotFoundException.class)
                .hasMessage("Feature flag 'flag-h' not found");
    }
}
