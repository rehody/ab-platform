package io.github.rehody.abplatform.dto.response;

import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditActorType;
import io.github.rehody.abplatform.model.audit.AuditEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExperimentAuditEventResponse(
        AuditAction action, ActorResponse actor, Instant timestamp, Map<String, String> details) {

    public static ExperimentAuditEventResponse from(AuditEvent auditEvent) {
        return new ExperimentAuditEventResponse(
                auditEvent.action(),
                ActorResponse.from(auditEvent.actor()),
                auditEvent.timestamp(),
                auditEvent.details().values());
    }

    public record ActorResponse(AuditActorType type, UUID userId, String systemName) {
        private static ActorResponse from(AuditActor auditActor) {
            return new ActorResponse(auditActor.type(), auditActor.userId(), auditActor.systemName());
        }
    }
}
