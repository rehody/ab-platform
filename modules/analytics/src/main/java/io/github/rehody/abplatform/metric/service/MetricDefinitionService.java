package io.github.rehody.abplatform.metric.service;

import io.github.rehody.abplatform.exception.MetricDefinitionNotFoundException;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.repository.MetricDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricDefinitionService {

    private final MetricDefinitionRepository metricDefinitionRepository;

    public MetricDefinition getByKey(String key) {
        return metricDefinitionRepository
                .findByKey(key)
                .orElseThrow(
                        () -> new MetricDefinitionNotFoundException("Metric definition '%s' not found".formatted(key)));
    }
}
