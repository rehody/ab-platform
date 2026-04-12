package io.github.rehody.abplatform.binding.policy;

import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.service.MetricDefinitionService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentMetricBindingPolicy {

    private final MetricDefinitionService metricDefinitionService;

    public List<String> prepareMetricKeys(List<String> metricKeys) {
        List<String> normalizedMetricKeys = normalizeMetricKeys(metricKeys);
        validateMetricKeysSupportedForEvaluation(normalizedMetricKeys);
        return normalizedMetricKeys;
    }

    private void validateMetricKeysSupportedForEvaluation(List<String> metricKeys) {
        for (String metricKey : metricKeys) {
            MetricDefinition metricDefinition = metricDefinitionService.getByKey(metricKey);
            if (!metricDefinition.isCountable()) {
                throw new IllegalArgumentException("Metric '%s' is not supported by evaluation".formatted(metricKey));
            }
        }
    }

    private List<String> normalizeMetricKeys(List<String> metricKeys) {
        Set<String> normalizedMetricKeys = new LinkedHashSet<>();

        for (String metricKey : metricKeys) {
            if (metricKey == null) {
                throw new IllegalArgumentException("metricKey must not be null");
            }

            String normalizedMetricKey = metricKey.trim();
            if (normalizedMetricKey.isEmpty()) {
                throw new IllegalArgumentException("metricKey must not be blank");
            }

            normalizedMetricKeys.add(normalizedMetricKey);
        }

        return List.copyOf(normalizedMetricKeys);
    }
}
