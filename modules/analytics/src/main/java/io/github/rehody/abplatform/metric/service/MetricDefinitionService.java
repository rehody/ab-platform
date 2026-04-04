package io.github.rehody.abplatform.metric.service;

import io.github.rehody.abplatform.cache.MetricDefinitionCache;
import io.github.rehody.abplatform.exception.MetricDefinitionAlreadyExistsException;
import io.github.rehody.abplatform.exception.MetricDefinitionNotFoundException;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.repository.MetricDefinitionRepository;
import io.github.rehody.abplatform.service.ServiceActionExecutor;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetricDefinitionService {

    private static final LockNamespace METRIC_DEFINITION_LOCK_NAMESPACE = LockNamespace.of("metric-definition");

    private final MetricDefinitionCache metricDefinitionCache;
    private final MetricDefinitionRepository metricDefinitionRepository;
    private final LockExecutor lockExecutor;
    private final ServiceActionExecutor serviceActionExecutor;

    @Transactional
    public MetricDefinition create(
            String key,
            String name,
            MetricType type,
            MetricDirection direction,
            MetricSeverity severity,
            BigDecimal deviationThreshold) {
        return executeUnderLock(key, () -> {
            ensureMetricDefinitionDoesNotExist(key);

            MetricDefinition metricDefinition =
                    new MetricDefinition(UUID.randomUUID(), key, name, type, direction, severity, deviationThreshold);

            metricDefinitionRepository.save(metricDefinition);
            invalidateCacheAfterCommit(key);
            return metricDefinition;
        });
    }

    private void ensureMetricDefinitionDoesNotExist(String key) {
        if (metricDefinitionRepository.existsByKey(key)) {
            throw new MetricDefinitionAlreadyExistsException("Metric definition '%s' already exists".formatted(key));
        }
    }

    @Transactional
    public MetricDefinition update(
            String key,
            String name,
            MetricType type,
            MetricDirection direction,
            MetricSeverity severity,
            BigDecimal deviationThreshold) {
        return executeUnderLock(key, () -> {
            MetricDefinition current = getByKey(key);
            MetricDefinition metricDefinition =
                    new MetricDefinition(current.id(), key, name, type, direction, severity, deviationThreshold);

            metricDefinitionRepository.update(metricDefinition);
            invalidateCacheAfterCommit(key);
            return metricDefinition;
        });
    }

    @Transactional(readOnly = true)
    public MetricDefinition getByKey(String key) {
        return metricDefinitionCache
                .getOrLoad(key, () -> metricDefinitionRepository.findByKey(key))
                .orElseThrow(
                        () -> new MetricDefinitionNotFoundException("Metric definition '%s' not found".formatted(key)));
    }

    @Transactional(readOnly = true)
    public List<MetricDefinition> getAll() {
        return metricDefinitionRepository.findAll();
    }

    private void invalidateCacheAfterCommit(String key) {
        serviceActionExecutor.executeAfterCommit(() -> metricDefinitionCache.invalidate(key));
    }

    private <T> T executeUnderLock(String key, Supplier<T> action) {
        return lockExecutor.withLock(METRIC_DEFINITION_LOCK_NAMESPACE, key, action);
    }
}
