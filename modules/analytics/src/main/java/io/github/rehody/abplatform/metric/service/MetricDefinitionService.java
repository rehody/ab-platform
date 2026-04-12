package io.github.rehody.abplatform.metric.service;

import io.github.rehody.abplatform.cache.MetricDefinitionCache;
import io.github.rehody.abplatform.exception.MetricDefinitionAlreadyExistsException;
import io.github.rehody.abplatform.exception.MetricDefinitionNotFoundException;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.repository.MetricDefinitionRepository;
import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditTarget;
import io.github.rehody.abplatform.service.ActionExecutorService;
import io.github.rehody.abplatform.service.AuditService;
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
    private final ActionExecutorService actionExecutorService;
    private final AuditService auditService;

    @Transactional
    public MetricDefinition create(
            AuditActor actor,
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
            writeCreateAudit(actor, metricDefinition);
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
            AuditActor actor,
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
            writeUpdateAudit(actor, current, metricDefinition);
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
        actionExecutorService.executeAfterCommit(() -> metricDefinitionCache.invalidate(key));
    }

    private <T> T executeUnderLock(String key, Supplier<T> action) {
        return lockExecutor.withLock(METRIC_DEFINITION_LOCK_NAMESPACE, key, action);
    }

    private void writeCreateAudit(AuditActor actor, MetricDefinition metricDefinition) {
        auditService.write(
                actor,
                AuditAction.METRIC_DEFINITION_CREATED,
                AuditTarget.metricDefinition(metricDefinition.id()),
                buildCreateDetails(metricDefinition));
    }

    private void writeUpdateAudit(
            AuditActor actor, MetricDefinition currentMetricDefinition, MetricDefinition updatedMetricDefinition) {
        auditService.write(
                actor,
                AuditAction.METRIC_DEFINITION_UPDATED,
                AuditTarget.metricDefinition(updatedMetricDefinition.id()),
                buildUpdateDetails(currentMetricDefinition, updatedMetricDefinition));
    }

    private AuditDetails buildCreateDetails(MetricDefinition metricDefinition) {
        return AuditDetails.entry("key", metricDefinition.key())
                .with("name", metricDefinition.name())
                .with("type", metricDefinition.type().toString())
                .with("direction", metricDefinition.direction().toString())
                .with("severity", metricDefinition.severity().toString())
                .with(
                        "deviationThreshold",
                        metricDefinition.deviationThreshold().toString());
    }

    private AuditDetails buildUpdateDetails(
            MetricDefinition currentMetricDefinition, MetricDefinition updatedMetricDefinition) {
        AuditDetails details = AuditDetails.empty();

        if (!currentMetricDefinition.name().equals(updatedMetricDefinition.name())) {
            details = details.with(
                    "name", "%s -> %s".formatted(currentMetricDefinition.name(), updatedMetricDefinition.name()));
        }

        if (currentMetricDefinition.type() != updatedMetricDefinition.type()) {
            details = details.with(
                    "type", "%s -> %s".formatted(currentMetricDefinition.type(), updatedMetricDefinition.type()));
        }

        if (currentMetricDefinition.direction() != updatedMetricDefinition.direction()) {
            details = details.with(
                    "direction",
                    "%s -> %s".formatted(currentMetricDefinition.direction(), updatedMetricDefinition.direction()));
        }

        if (currentMetricDefinition.severity() != updatedMetricDefinition.severity()) {
            details = details.with(
                    "severity",
                    "%s -> %s".formatted(currentMetricDefinition.severity(), updatedMetricDefinition.severity()));
        }

        if (currentMetricDefinition.deviationThreshold().compareTo(updatedMetricDefinition.deviationThreshold()) != 0) {
            details = details.with(
                    "deviationThreshold",
                    "%s -> %s"
                            .formatted(
                                    currentMetricDefinition.deviationThreshold(),
                                    updatedMetricDefinition.deviationThreshold()));
        }

        return details;
    }
}
