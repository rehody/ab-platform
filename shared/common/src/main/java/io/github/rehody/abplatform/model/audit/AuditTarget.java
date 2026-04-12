package io.github.rehody.abplatform.model.audit;

import java.util.UUID;

public record AuditTarget(AuditTargetType type, UUID id) {

    public AuditTarget {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
    }

    public static AuditTarget experiment(UUID id) {
        return new AuditTarget(AuditTargetType.EXPERIMENT, id);
    }

    public static AuditTarget featureFlag(UUID id) {
        return new AuditTarget(AuditTargetType.FEATURE_FLAG, id);
    }

    public static AuditTarget experimentRisk(UUID id) {
        return new AuditTarget(AuditTargetType.EXPERIMENT_RISK, id);
    }

    public static AuditTarget metricDefinition(UUID id) {
        return new AuditTarget(AuditTargetType.METRIC_DEFINITION, id);
    }
}
