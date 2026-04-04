package io.github.rehody.abplatform.evaluation.policy;

import io.github.rehody.abplatform.binding.repository.ExperimentMetricBindingRepository;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.service.MetricDefinitionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentMetricEvaluationPolicy {

    private final ExperimentMetricBindingRepository experimentMetricBindingRepository;
    private final MetricDefinitionService metricDefinitionService;

    public MetricDefinition getMetricDefinitionForEvaluation(UUID experimentId, String metricKey) {
        validateMetricBoundToExperiment(experimentId, metricKey);

        MetricDefinition metricDefinition = metricDefinitionService.getByKey(metricKey);
        if (!metricDefinition.isCountable()) {
            throw new IllegalArgumentException(
                    "Metric '%s' is not supported by evaluation".formatted(metricDefinition.key()));
        }

        return metricDefinition;
    }

    private void validateMetricBoundToExperiment(UUID experimentId, String metricKey) {
        List<String> metricKeys = experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId);
        if (!metricKeys.contains(metricKey)) {
            throw new IllegalArgumentException(
                    "Metric '%s' is not bound to experiment '%s'".formatted(metricKey, experimentId));
        }
    }
}
