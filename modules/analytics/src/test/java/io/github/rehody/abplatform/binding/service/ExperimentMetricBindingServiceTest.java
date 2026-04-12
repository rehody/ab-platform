package io.github.rehody.abplatform.binding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingConflictPolicy;
import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingPolicy;
import io.github.rehody.abplatform.binding.repository.ExperimentMetricBindingRepository;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.service.ActionExecutorService;
import io.github.rehody.abplatform.service.AuditService;
import io.github.rehody.abplatform.service.ExperimentQueryService;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ExperimentMetricBindingServiceTest {

    @Mock
    private ExperimentMetricBindingRepository experimentMetricBindingRepository;

    @Mock
    private ExperimentQueryService experimentQueryService;

    @Mock
    private ExperimentMetricBindingPolicy experimentMetricBindingPolicy;

    @Mock
    private ExperimentMetricBindingConflictPolicy experimentMetricBindingConflictPolicy;

    @Mock
    private ExperimentMetricBindingCacheInvalidator experimentMetricBindingCacheInvalidator;

    @Mock
    private LockExecutor lockExecutor;

    @Mock
    private AuditService auditService;

    private ExperimentMetricBindingService experimentMetricBindingService;

    @BeforeEach
    void setUp() {
        ActionExecutorService actionExecutorService = new ActionExecutorService();
        experimentMetricBindingService = new ExperimentMetricBindingService(
                experimentMetricBindingRepository,
                experimentQueryService,
                experimentMetricBindingPolicy,
                experimentMetricBindingConflictPolicy,
                experimentMetricBindingCacheInvalidator,
                lockExecutor,
                actionExecutorService,
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
    void shouldUpdateMetricKeysUnderLockWhenBindingsAreReplaced() {
        UUID experimentId = UUID.randomUUID();
        List<String> requestedMetricKeys = List.of("metric-shared", "metric-new");
        List<String> normalizedMetricKeys = List.of("metric-shared", "metric-new");
        Experiment experiment = new Experiment(
                experimentId,
                "checkout-redesign",
                "CHECKOUT",
                ExperimentRolloutPlan.initial(),
                List.of(),
                ExperimentState.DRAFT,
                2L,
                null,
                null);
        when(experimentQueryService.getById(experimentId)).thenReturn(experiment);
        when(experimentMetricBindingPolicy.prepareMetricKeys(requestedMetricKeys))
                .thenReturn(normalizedMetricKeys);
        when(experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId))
                .thenReturn(List.of("metric-old", "metric-shared"));

        List<String> response = experimentMetricBindingService.updateMetricKeys(
                AuditActor.user(UUID.randomUUID()), experimentId, requestedMetricKeys);

        assertThat(response).isEqualTo(normalizedMetricKeys);
        verify(experimentMetricBindingRepository).updateMetricKeys(experimentId, normalizedMetricKeys);
    }

    @Test
    void shouldInvalidateAffectedReportKeysAfterCommitWhenTransactionCommits() {
        UUID experimentId = UUID.randomUUID();
        List<String> requestedMetricKeys = List.of("metric-shared", "metric-new");
        List<String> normalizedMetricKeys = List.of("metric-shared", "metric-new");
        Experiment experiment = new Experiment(
                experimentId,
                "checkout-redesign",
                "CHECKOUT",
                ExperimentRolloutPlan.initial(),
                List.of(),
                ExperimentState.DRAFT,
                2L,
                null,
                null);
        when(experimentQueryService.getById(experimentId)).thenReturn(experiment);
        when(experimentMetricBindingPolicy.prepareMetricKeys(requestedMetricKeys))
                .thenReturn(normalizedMetricKeys);
        when(experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId))
                .thenReturn(List.of("metric-old", "metric-shared"));
        TransactionSynchronizationManager.initSynchronization();

        experimentMetricBindingService.updateMetricKeys(
                AuditActor.user(UUID.randomUUID()), experimentId, requestedMetricKeys);

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
        Experiment experiment = new Experiment(
                experimentId,
                "checkout-redesign",
                "CHECKOUT",
                ExperimentRolloutPlan.initial(),
                List.of(),
                ExperimentState.RUNNING,
                4L,
                null,
                null);

        when(experimentQueryService.getById(experimentId)).thenReturn(experiment);
        when(experimentMetricBindingPolicy.prepareMetricKeys(requestedMetricKeys))
                .thenReturn(normalizedMetricKeys);
        when(experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId))
                .thenReturn(List.of());

        experimentMetricBindingService.updateMetricKeys(
                AuditActor.user(UUID.randomUUID()), experimentId, requestedMetricKeys);

        verify(experimentMetricBindingConflictPolicy)
                .validateNoRunningMetricConflicts(eq(experimentId), eq(normalizedMetricKeys));
    }

    @Test
    void shouldGetMetricKeysAfterEnsuringExperimentExists() {
        UUID experimentId = UUID.randomUUID();
        when(experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId))
                .thenReturn(List.of("orders", "revenue"));

        List<String> response = experimentMetricBindingService.getMetricKeys(experimentId);

        assertThat(response).containsExactly("orders", "revenue");
        verify(experimentQueryService).ensureExistsById(experimentId);
    }
}
