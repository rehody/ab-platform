package io.github.rehody.abplatform.binding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingConflictPolicy;
import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingPolicy;
import io.github.rehody.abplatform.binding.repository.ExperimentMetricBindingRepository;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.service.ExperimentService;
import io.github.rehody.abplatform.service.ServiceActionExecutor;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ExperimentMetricBindingServiceTest {

    @Mock
    private ExperimentMetricBindingRepository experimentMetricBindingRepository;

    @Mock
    private ExperimentService experimentService;

    @Mock
    private ExperimentMetricBindingPolicy experimentMetricBindingPolicy;

    @Mock
    private ExperimentMetricBindingConflictPolicy experimentMetricBindingConflictPolicy;

    @Mock
    private ExperimentMetricBindingCacheInvalidator experimentMetricBindingCacheInvalidator;

    @Mock
    private LockExecutor lockExecutor;

    private ExperimentMetricBindingService experimentMetricBindingService;

    @BeforeEach
    void setUp() {
        ServiceActionExecutor serviceActionExecutor = new ServiceActionExecutor();
        experimentMetricBindingService = new ExperimentMetricBindingService(
                experimentMetricBindingRepository,
                experimentService,
                experimentMetricBindingPolicy,
                experimentMetricBindingConflictPolicy,
                experimentMetricBindingCacheInvalidator,
                lockExecutor,
                serviceActionExecutor);
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
    void shouldUpdateMetricKeysUnderLockWhenBindingsAreReplaced() {
        UUID experimentId = UUID.randomUUID();
        List<String> requestedMetricKeys = List.of("metric-shared", "metric-new");
        List<String> normalizedMetricKeys = List.of("metric-shared", "metric-new");
        Experiment experiment =
                new Experiment(experimentId, "checkout-redesign", List.of(), ExperimentState.DRAFT, 2L, null, null);
        when(experimentService.getById(experimentId)).thenReturn(experiment);
        when(experimentMetricBindingPolicy.prepareMetricKeys(requestedMetricKeys))
                .thenReturn(normalizedMetricKeys);
        when(experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId))
                .thenReturn(List.of("metric-old", "metric-shared"));

        List<String> response = experimentMetricBindingService.updateMetricKeys(experimentId, requestedMetricKeys);

        assertThat(response).isEqualTo(normalizedMetricKeys);
        ArgumentCaptor<LockNamespace> namespaceCaptor = ArgumentCaptor.forClass(LockNamespace.class);
        verify(lockExecutor).withLock(namespaceCaptor.capture(), eq(experimentId.toString()), any(Supplier.class));
        assertThat(namespaceCaptor.getValue().value()).isEqualTo("experiment-metric-binding");
        verify(experimentMetricBindingRepository).updateMetricKeys(experimentId, normalizedMetricKeys);
    }

    @Test
    void shouldInvalidateAffectedReportKeysAfterCommitWhenTransactionCommits() {
        UUID experimentId = UUID.randomUUID();
        List<String> requestedMetricKeys = List.of("metric-shared", "metric-new");
        List<String> normalizedMetricKeys = List.of("metric-shared", "metric-new");
        Experiment experiment =
                new Experiment(experimentId, "checkout-redesign", List.of(), ExperimentState.DRAFT, 2L, null, null);
        when(experimentService.getById(experimentId)).thenReturn(experiment);
        when(experimentMetricBindingPolicy.prepareMetricKeys(requestedMetricKeys))
                .thenReturn(normalizedMetricKeys);
        when(experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId))
                .thenReturn(List.of("metric-old", "metric-shared"));
        TransactionSynchronizationManager.initSynchronization();

        experimentMetricBindingService.updateMetricKeys(experimentId, requestedMetricKeys);

        verify(experimentMetricBindingCacheInvalidator, never()).invalidateReports(any(), any());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(experimentMetricBindingCacheInvalidator)
                .invalidateReports(experimentId, List.of("metric-old", "metric-shared", "metric-new"));
    }

    @Test
    void shouldValidateRunningConflictsInsideLockWhenExperimentIsRunning() {
        UUID experimentId = UUID.randomUUID();
        List<String> requestedMetricKeys = List.of("metric-a");
        List<String> normalizedMetricKeys = List.of("metric-a");
        Experiment experiment =
                new Experiment(experimentId, "checkout-redesign", List.of(), ExperimentState.RUNNING, 4L, null, null);
        AtomicBoolean lockHeld = new AtomicBoolean(false);

        when(experimentService.getById(experimentId)).thenReturn(experiment);
        when(experimentMetricBindingPolicy.prepareMetricKeys(requestedMetricKeys))
                .thenReturn(normalizedMetricKeys);
        when(experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId))
                .thenReturn(List.of());
        when(lockExecutor.withLock(any(LockNamespace.class), any(String.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    lockHeld.set(true);
                    try {
                        return ((Supplier<?>) invocation.getArgument(2)).get();
                    } finally {
                        lockHeld.set(false);
                    }
                });
        doAnswer(invocation -> {
                    assertThat(lockHeld.get()).isTrue();
                    return null;
                })
                .when(experimentMetricBindingConflictPolicy)
                .validateNoRunningMetricConflicts(experimentId, normalizedMetricKeys);

        experimentMetricBindingService.updateMetricKeys(experimentId, requestedMetricKeys);

        verify(experimentMetricBindingConflictPolicy)
                .validateNoRunningMetricConflicts(eq(experimentId), eq(normalizedMetricKeys));
    }
}
