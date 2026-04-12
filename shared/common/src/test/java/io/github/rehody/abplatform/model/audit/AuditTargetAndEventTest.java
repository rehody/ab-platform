package io.github.rehody.abplatform.model.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditTargetAndEventTest {

    @Test
    void targetFactories_shouldCreateTypedTargets() {
        UUID id = UUID.randomUUID();

        assertThat(AuditTarget.experiment(id).type()).isEqualTo(AuditTargetType.EXPERIMENT);
        assertThat(AuditTarget.featureFlag(id).type()).isEqualTo(AuditTargetType.FEATURE_FLAG);
        assertThat(AuditTarget.experimentRisk(id).type()).isEqualTo(AuditTargetType.EXPERIMENT_RISK);
        assertThat(AuditTarget.metricDefinition(id).type()).isEqualTo(AuditTargetType.METRIC_DEFINITION);
    }

    @Test
    void targetConstructor_shouldRejectNulls() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new AuditTarget(null, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("type must not be null");
        assertThatThrownBy(() -> new AuditTarget(AuditTargetType.EXPERIMENT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must not be null");
    }

    @Test
    void auditEvent_shouldDefaultDetailsAndValidateRequiredFields() {
        AuditActor actor = AuditActor.system("scheduler");
        AuditTarget target = AuditTarget.experiment(UUID.randomUUID());
        AuditEvent auditEvent = AuditEvent.create(AuditAction.EXPERIMENT_CREATED, actor, target, null);

        assertThat(auditEvent.action()).isEqualTo(AuditAction.EXPERIMENT_CREATED);
        assertThat(auditEvent.details().values()).isEmpty();
        assertThat(auditEvent.timestamp()).isNotNull();

        assertThatThrownBy(() -> new AuditEvent(null, actor, target, auditEvent.timestamp(), AuditDetails.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null");
    }
}
