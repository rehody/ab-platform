package io.github.rehody.abplatform.model.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditActorTest {

    @Test
    void userFactories_shouldCreateUserActor() {
        UUID userId = UUID.randomUUID();

        AuditActor fromUuid = AuditActor.user(userId);
        AuditActor fromString = AuditActor.user(userId.toString());

        assertThat(fromUuid.type()).isEqualTo(AuditActorType.USER);
        assertThat(fromUuid.userId()).isEqualTo(userId);
        assertThat(fromUuid.systemName()).isNull();
        assertThat(fromString).isEqualTo(fromUuid);
    }

    @Test
    void userFactory_shouldRejectBlankOrInvalidUserId() {
        assertThatThrownBy(() -> AuditActor.user((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must not be blank");
        assertThatThrownBy(() -> AuditActor.user(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must not be blank");
        assertThatThrownBy(() -> AuditActor.user("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be a valid UUID");
    }

    @Test
    void systemFactory_shouldCreateSystemActor() {
        AuditActor auditActor = AuditActor.system("scheduler");

        assertThat(auditActor.type()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(auditActor.userId()).isNull();
        assertThat(auditActor.systemName()).isEqualTo("scheduler");
    }

    @Test
    void constructor_shouldValidateActorShape() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> new AuditActor(null, userId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("type must not be null");
        assertThatThrownBy(() -> new AuditActor(AuditActorType.USER, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userId must not be null");
        assertThatThrownBy(() -> new AuditActor(AuditActorType.USER, userId, "system"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("systemName must be null");
        assertThatThrownBy(() -> new AuditActor(AuditActorType.SYSTEM, userId, "system"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be null");
        assertThatThrownBy(() -> new AuditActor(AuditActorType.SYSTEM, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("systemName must not be blank");
        assertThatThrownBy(() -> new AuditActor(AuditActorType.SYSTEM, null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("systemName must not be blank");
    }
}
