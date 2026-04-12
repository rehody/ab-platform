package io.github.rehody.abplatform.event.service;

import io.github.rehody.abplatform.event.model.MetricEvent;
import io.github.rehody.abplatform.event.repository.MetricEventRepository;
import io.github.rehody.abplatform.exception.MetricEventAlreadyExistsException;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.service.MetricDefinitionService;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetricEventService {

    private static final LockNamespace METRIC_EVENT_LOCK_NAMESPACE = LockNamespace.of("metric-event");

    private final LockExecutor lockExecutor;
    private final MetricDefinitionService metricDefinitionService;
    private final MetricEventRepository metricEventRepository;

    @Transactional
    public MetricEvent create(UUID userId, String metricKey) {
        MetricDefinition metricDefinition = metricDefinitionService.getByKey(metricKey);
        return switch (metricDefinition.type()) {
            case COUNTABLE -> createAndSaveMetricEvent(userId, metricDefinition);
            case UNIQUE -> createUniqueMetricEvent(userId, metricDefinition);
        };
    }

    private MetricEvent createUniqueMetricEvent(UUID userId, MetricDefinition metricDefinition) {
        String lockKey = buildUniqueMetricEventLockKey(userId, metricDefinition);
        return executeUnderLock(lockKey, () -> {
            ensureUniqueMetricEventDoesNotExist(userId, metricDefinition);
            return createAndSaveMetricEvent(userId, metricDefinition);
        });
    }

    private void ensureUniqueMetricEventDoesNotExist(UUID userId, MetricDefinition metricDefinition) {
        if (metricEventRepository.existsUniqueEventForUser(userId, metricDefinition.key())) {
            throw new MetricEventAlreadyExistsException("Metric event for user '%s' and metric '%s' already exists"
                    .formatted(userId, metricDefinition.key()));
        }
    }

    private String buildUniqueMetricEventLockKey(UUID userId, MetricDefinition metricDefinition) {
        return "%s:%s".formatted(metricDefinition.key(), userId);
    }

    private MetricEvent createAndSaveMetricEvent(UUID userId, MetricDefinition metricDefinition) {
        MetricEvent metricEvent = buildMetricEvent(userId, metricDefinition);
        metricEventRepository.save(metricEvent);
        return metricEvent;
    }

    private <T> T executeUnderLock(String key, Supplier<T> action) {
        return lockExecutor.withLock(METRIC_EVENT_LOCK_NAMESPACE, key, action);
    }

    private MetricEvent buildMetricEvent(UUID userId, MetricDefinition metricDefinition) {
        return new MetricEvent(UUID.randomUUID(), userId, metricDefinition.key(), Instant.now());
    }
}
