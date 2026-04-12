package io.github.rehody.abplatform.binding.service;

import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingConflictPolicy;
import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingPolicy;
import io.github.rehody.abplatform.binding.repository.ExperimentMetricBindingRepository;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditTarget;
import io.github.rehody.abplatform.service.ActionExecutorService;
import io.github.rehody.abplatform.service.AuditService;
import io.github.rehody.abplatform.service.ExperimentQueryService;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentMetricBindingService {

    private static final LockNamespace EXPERIMENT_METRIC_BINDING_LOCK_NAMESPACE =
            LockNamespace.of("experiment-metric-binding");

    private final ExperimentMetricBindingRepository experimentMetricBindingRepository;
    private final ExperimentQueryService experimentQueryService;
    private final ExperimentMetricBindingPolicy experimentMetricBindingPolicy;
    private final ExperimentMetricBindingConflictPolicy experimentMetricBindingConflictPolicy;
    private final ExperimentMetricBindingCacheInvalidator experimentMetricBindingCacheInvalidator;
    private final LockExecutor lockExecutor;
    private final ActionExecutorService actionExecutorService;
    private final AuditService auditService;

    @Transactional
    public List<String> updateMetricKeys(AuditActor actor, UUID experimentId, List<String> metricKeys) {
        return executeUnderLock(experimentId, () -> {
            Experiment experiment = experimentQueryService.getById(experimentId);
            List<String> preparedMetricKeys = experimentMetricBindingPolicy.prepareMetricKeys(metricKeys);

            if (experiment.isRunning()) {
                experimentMetricBindingConflictPolicy.validateNoRunningMetricConflicts(
                        experimentId, preparedMetricKeys);
            }

            List<String> previousMetricKeys =
                    experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId);
            experimentMetricBindingRepository.updateMetricKeys(experimentId, preparedMetricKeys);
            writeUpdateAudit(actor, experimentId, previousMetricKeys, preparedMetricKeys);
            invalidateReportsAfterCommit(experimentId, previousMetricKeys, preparedMetricKeys);
            return preparedMetricKeys;
        });
    }

    @Transactional(readOnly = true)
    public List<String> getMetricKeys(UUID experimentId) {
        experimentQueryService.ensureExistsById(experimentId);
        return experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId);
    }

    private void invalidateReportsAfterCommit(
            UUID experimentId, List<String> previousMetricKeys, List<String> currentMetricKeys) {
        List<String> affectedMetricKeys = collectAffectedMetricKeys(previousMetricKeys, currentMetricKeys);
        actionExecutorService.executeAfterCommit(
                () -> experimentMetricBindingCacheInvalidator.invalidateReports(experimentId, affectedMetricKeys));
    }

    private List<String> collectAffectedMetricKeys(List<String> previousMetricKeys, List<String> currentMetricKeys) {
        Set<String> affectedMetricKeys = new LinkedHashSet<>(previousMetricKeys);
        affectedMetricKeys.addAll(currentMetricKeys);
        return List.copyOf(affectedMetricKeys);
    }

    private void writeUpdateAudit(
            AuditActor actor, UUID experimentId, List<String> previousMetricKeys, List<String> currentMetricKeys) {
        auditService.write(
                actor,
                AuditAction.EXPERIMENT_METRIC_BINDINGS_UPDATED,
                AuditTarget.experiment(experimentId),
                AuditDetails.metricBindingsTransition(previousMetricKeys, currentMetricKeys));
    }

    private <T> T executeUnderLock(UUID experimentId, Supplier<T> action) {
        return lockExecutor.withLock(EXPERIMENT_METRIC_BINDING_LOCK_NAMESPACE, experimentId.toString(), action);
    }
}
