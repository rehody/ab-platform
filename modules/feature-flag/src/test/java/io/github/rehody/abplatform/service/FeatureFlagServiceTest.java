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
import io.github.rehody.abplatform.exception.FeatureFlagAlreadyExistsException;
import io.github.rehody.abplatform.exception.FeatureFlagNotFoundException;
import io.github.rehody.abplatform.exception.FeatureFlagUpdateBlockedException;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.policy.FeatureFlagUpdatePolicy;
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

    private static final AuditActor ACTOR = AuditActor.user(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @Mock
    private LockExecutor lockExecutor;

    @Mock
    private FeatureFlagCache featureFlagCache;

    @Mock
    private FeatureFlagUpdatePolicy featureFlagUpdatePolicy;

    @Mock
    private AuditService auditService;

    private FeatureFlagService featureFlagService;

    @BeforeEach
    void setUp() {
        ActionExecutorService actionExecutorService = new ActionExecutorService();
        featureFlagService = new FeatureFlagService(
                featureFlagRepository,
                featureFlagUpdatePolicy,
                lockExecutor,
                actionExecutorService,
                featureFlagCache,
                auditService);
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
        FeatureValue defaultValue = new FeatureValue(true, FeatureValueType.BOOL);
        when(featureFlagRepository.existsByKey("flag-a")).thenReturn(true);

        assertThatThrownBy(() -> featureFlagService.create(ACTOR, "flag-a", defaultValue))
                .isInstanceOf(FeatureFlagAlreadyExistsException.class)
                .hasMessage("Feature flag 'flag-a' already exists");

        verify(featureFlagRepository, never()).save(any());
        verify(featureFlagCache, never()).invalidate(any());
    }

    @Test
    void create_shouldSaveFeatureFlagAndInvalidateCacheWhenSynchronizationInactive() {
        FeatureValue defaultValue = new FeatureValue("v1", FeatureValueType.STRING);
        when(featureFlagRepository.existsByKey("flag-b")).thenReturn(false);

        FeatureFlag response = featureFlagService.create(ACTOR, "flag-b", defaultValue);

        ArgumentCaptor<FeatureFlag> featureFlagCaptor = ArgumentCaptor.forClass(FeatureFlag.class);

        verify(featureFlagRepository).save(featureFlagCaptor.capture());
        verify(featureFlagCache).invalidate("flag-b");

        FeatureFlag savedFeatureFlag = featureFlagCaptor.getValue();
        assertThat(savedFeatureFlag.id()).isNotNull();
        assertThat(savedFeatureFlag.key()).isEqualTo("flag-b");
        assertThat(savedFeatureFlag.defaultValue()).isEqualTo(defaultValue);
        assertThat(savedFeatureFlag.version()).isZero();

        assertThat(response.key()).isEqualTo("flag-b");
        assertThat(response.defaultValue()).isEqualTo(defaultValue);
        assertThat(response.version()).isZero();
    }

    @Test
    void create_shouldRegisterAfterCommitActionAndInvalidateCacheAfterCommitWhenSynchronizationActive() {
        FeatureValue defaultValue = new FeatureValue(10, FeatureValueType.NUMBER);
        when(featureFlagRepository.existsByKey("flag-c")).thenReturn(false);

        TransactionSynchronizationManager.initSynchronization();

        FeatureFlag response = featureFlagService.create(ACTOR, "flag-c", defaultValue);

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
        FeatureFlag persisted = new FeatureFlag(UUID.randomUUID(), "flag-d", defaultValue, 42L);

        when(featureFlagUpdatePolicy.canUpdateDefaultValue("flag-d")).thenReturn(true);
        when(featureFlagRepository.update("flag-d", defaultValue, 3L)).thenReturn(1);
        when(featureFlagRepository.findByKey("flag-d")).thenReturn(Optional.of(persisted));

        FeatureFlag response = featureFlagService.update(ACTOR, "flag-d", defaultValue, 3L);

        verify(featureFlagRepository).update("flag-d", defaultValue, 3L);
        verify(featureFlagCache).invalidate("flag-d");
        assertThat(response.key()).isEqualTo("flag-d");
        assertThat(response.defaultValue()).isEqualTo(defaultValue);
        assertThat(response.version()).isEqualTo(42L);
    }

    @Test
    void update_shouldThrowFeatureFlagNotFoundExceptionAndSkipCacheInvalidationWhenFeatureFlagMissing() {
        FeatureValue defaultValue = new FeatureValue(false, FeatureValueType.BOOL);
        FeatureFlag existing = new FeatureFlag(UUID.randomUUID(), "flag-e", defaultValue, 4L);

        when(featureFlagUpdatePolicy.canUpdateDefaultValue("flag-e")).thenReturn(true);
        when(featureFlagRepository.findByKey("flag-e")).thenReturn(Optional.of(existing));
        when(featureFlagRepository.update("flag-e", defaultValue, 5L)).thenReturn(0);
        when(featureFlagRepository.existsByKey("flag-e")).thenReturn(false);

        assertThatThrownBy(() -> featureFlagService.update(ACTOR, "flag-e", defaultValue, 5L))
                .isInstanceOf(FeatureFlagNotFoundException.class)
                .hasMessage("Feature flag 'flag-e' not found");

        verify(featureFlagRepository).update("flag-e", defaultValue, 5L);
        verify(featureFlagCache, never()).invalidate("flag-e");
    }

    @Test
    void update_shouldThrowOptimisticLockingFailureExceptionWhenVersionMismatch() {
        FeatureValue defaultValue = new FeatureValue(false, FeatureValueType.BOOL);
        FeatureFlag existing = new FeatureFlag(UUID.randomUUID(), "flag-e", defaultValue, 4L);

        when(featureFlagUpdatePolicy.canUpdateDefaultValue("flag-e")).thenReturn(true);
        when(featureFlagRepository.findByKey("flag-e")).thenReturn(Optional.of(existing));
        when(featureFlagRepository.existsByKey("flag-e")).thenReturn(true);
        when(featureFlagRepository.update("flag-e", defaultValue, 2L)).thenReturn(0);

        assertThatThrownBy(() -> featureFlagService.update(ACTOR, "flag-e", defaultValue, 2L))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage("Feature flag 'flag-e' version mismatch. Expected version 2");

        verify(featureFlagRepository).update("flag-e", defaultValue, 2L);
        verify(featureFlagCache, never()).invalidate("flag-e");
    }

    @Test
    void update_shouldThrowFeatureFlagUpdateBlockedExceptionWhenExperimentExistsForFlag() {
        FeatureValue defaultValue = new FeatureValue(false, FeatureValueType.BOOL);
        FeatureFlag existing = new FeatureFlag(UUID.randomUUID(), "flag-i", defaultValue, 4L);

        when(featureFlagRepository.findByKey("flag-i")).thenReturn(Optional.of(existing));
        when(featureFlagUpdatePolicy.canUpdateDefaultValue("flag-i")).thenReturn(false);

        assertThatThrownBy(() -> featureFlagService.update(ACTOR, "flag-i", defaultValue, 1L))
                .isInstanceOf(FeatureFlagUpdateBlockedException.class)
                .hasMessage("Feature flag 'flag-i' default value cannot be updated while an experiment exists");

        verify(featureFlagRepository, never()).update(any(), any(), any(Long.class));
        verify(featureFlagCache, never()).invalidate(any());
    }

    @Test
    void getByKey_shouldLoadFromRepositoryAndReturnResponseWhenCacheNeedsLoader() {
        String key = "flag-f";
        FeatureFlag persisted =
                new FeatureFlag(UUID.randomUUID(), key, new FeatureValue("enabled", FeatureValueType.STRING), 8L);

        when(featureFlagRepository.findByKey(key)).thenReturn(Optional.of(persisted));
        when(featureFlagCache.getOrLoad(eq(key), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<Optional<FeatureFlag>> loader = invocation.getArgument(1);
            return loader.get();
        });

        FeatureFlag response = featureFlagService.getByKey(key);

        assertThat(response.key()).isEqualTo(key);
        assertThat(response.defaultValue()).isEqualTo(persisted.defaultValue());
        assertThat(response.version()).isEqualTo(8L);
    }

    @Test
    void getByKey_shouldReturnCachedResponseWhenCacheHit() {
        String key = "flag-g";
        FeatureFlag featureFlag =
                new FeatureFlag(UUID.randomUUID(), key, new FeatureValue(true, FeatureValueType.BOOL), 12L);

        when(featureFlagCache.getOrLoad(eq(key), any(Supplier.class))).thenReturn(Optional.of(featureFlag));

        FeatureFlag response = featureFlagService.getByKey(key);

        assertThat(response).isEqualTo(featureFlag);
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
