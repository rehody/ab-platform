package io.github.rehody.abplatform.binding.service;

import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingConflictPolicy;
import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingPolicy;
import io.github.rehody.abplatform.binding.repository.ExperimentMetricBindingRepository;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.service.ExperimentService;
import io.github.rehody.abplatform.service.ServiceActionExecutor;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentMetricBindingService {

    private final ExperimentMetricBindingRepository experimentMetricBindingRepository;
    private final ExperimentService experimentService;
    private final ExperimentMetricBindingPolicy experimentMetricBindingPolicy;
    private final ExperimentMetricBindingConflictPolicy experimentMetricBindingConflictPolicy;
    private final ExperimentMetricBindingCacheInvalidator experimentMetricBindingCacheInvalidator;
    private final ServiceActionExecutor serviceActionExecutor;

    @Transactional
    public List<String> updateMetricKeys(UUID experimentId, List<String> metricKeys) {
        Experiment experiment = experimentService.getById(experimentId);
        List<String> preparedMetricKeys = experimentMetricBindingPolicy.prepareMetricKeys(metricKeys);

        if (experiment.isRunning()) {
            experimentMetricBindingConflictPolicy.validateNoRunningMetricConflicts(experimentId, preparedMetricKeys);
        }

        List<String> previousMetricKeys = experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId);
        experimentMetricBindingRepository.updateMetricKeys(experimentId, preparedMetricKeys);
        invalidateReportsAfterCommit(experimentId, previousMetricKeys, preparedMetricKeys);
        return preparedMetricKeys;
    }

    @Transactional(readOnly = true)
    public List<String> getMetricKeys(UUID experimentId) {
        experimentService.ensureExistsById(experimentId);
        return experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId);
    }

    private void invalidateReportsAfterCommit(
            UUID experimentId, List<String> previousMetricKeys, List<String> currentMetricKeys) {
        List<String> affectedMetricKeys = collectAffectedMetricKeys(previousMetricKeys, currentMetricKeys);
        serviceActionExecutor.executeAfterCommit(
                () -> experimentMetricBindingCacheInvalidator.invalidateReports(experimentId, affectedMetricKeys));
    }

    private List<String> collectAffectedMetricKeys(List<String> previousMetricKeys, List<String> currentMetricKeys) {
        Set<String> affectedMetricKeys = new LinkedHashSet<>(previousMetricKeys);
        affectedMetricKeys.addAll(currentMetricKeys);
        return List.copyOf(affectedMetricKeys);
    }
}
