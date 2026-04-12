package io.github.rehody.abplatform.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditActorType;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditEvent;
import io.github.rehody.abplatform.model.audit.AuditTarget;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentAuditEventResponseTest {

    @Test
    void from_shouldMapAuditEventToResponse() {
        UUID userId = UUID.randomUUID();
        AuditEvent auditEvent = new AuditEvent(
                AuditAction.EXPERIMENT_CREATED,
                AuditActor.user(userId),
                AuditTarget.experiment(UUID.randomUUID()),
                Instant.parse("2026-04-12T10:00:00Z"),
                AuditDetails.entry("state", "DRAFT"));

        ExperimentAuditEventResponse response = ExperimentAuditEventResponse.from(auditEvent);

        assertThat(response.action()).isEqualTo(AuditAction.EXPERIMENT_CREATED);
        assertThat(response.timestamp()).isEqualTo(auditEvent.timestamp());
        assertThat(response.details()).containsEntry("state", "DRAFT");
        assertThat(response.actor().type()).isEqualTo(AuditActorType.USER);
        assertThat(response.actor().userId()).isEqualTo(userId);
        assertThat(response.actor().systemName()).isNull();
    }
}
