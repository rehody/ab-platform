package io.github.rehody.abplatform.model.audit;

import java.time.Instant;
import java.util.Objects;

public record AuditEvent(
        AuditAction action, AuditActor actor, AuditTarget target, Instant timestamp, AuditDetails details) {

    public AuditEvent {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        details = details == null ? AuditDetails.empty() : details;
    }

    public static AuditEvent create(AuditAction action, AuditActor actor, AuditTarget target, AuditDetails details) {
        return new AuditEvent(action, actor, target, Instant.now(), details);
    }
}
