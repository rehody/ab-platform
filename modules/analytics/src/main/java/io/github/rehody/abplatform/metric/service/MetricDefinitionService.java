package io.github.rehody.abplatform.metric.service;

import io.github.rehody.abplatform.cache.CachedMetricDefinition;
import io.github.rehody.abplatform.cache.MetricDefinitionCache;
import io.github.rehody.abplatform.exception.MetricDefinitionNotFoundException;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.repository.MetricDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetricDefinitionService {

    private final MetricDefinitionCache metricDefinitionCache;
    private final MetricDefinitionRepository metricDefinitionRepository;

    @Transactional(readOnly = true)
    public MetricDefinition getByKey(String key) {
        return metricDefinitionCache
                .getOrLoad(key, () -> metricDefinitionRepository.findByKey(key).map(CachedMetricDefinition::from))
                .map(CachedMetricDefinition::toModel)
                .orElseThrow(
                        () -> new MetricDefinitionNotFoundException("Metric definition '%s' not found".formatted(key)));
    }
}
