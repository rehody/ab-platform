package io.github.rehody.abplatform.model.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.PlatformRole;
import org.junit.jupiter.api.Test;

class AuditEnumsAndPermissionsTest {

    @Test
    void enums_shouldExposeDeclaredValues() {
        assertThat(AuditActorType.values()).containsExactly(AuditActorType.USER, AuditActorType.SYSTEM);
        assertThat(AuditTargetType.values())
                .containsExactly(
                        AuditTargetType.EXPERIMENT,
                        AuditTargetType.FEATURE_FLAG,
                        AuditTargetType.EXPERIMENT_RISK,
                        AuditTargetType.METRIC_DEFINITION);
        assertThat(AuditAction.values())
                .contains(
                        AuditAction.EXPERIMENT_CREATED,
                        AuditAction.EXPERIMENT_PAUSED_AUTO_AFTER_ROLLOUT_ROLLBACK,
                        AuditAction.METRIC_DEFINITION_UPDATED,
                        AuditAction.FEATURE_FLAG_UPDATED);
        assertThat(PlatformRole.values())
                .containsExactly(PlatformRole.VIEWER, PlatformRole.EXPERIMENTER, PlatformRole.OPERATOR);
        assertThat(PlatformPermission.values())
                .contains(
                        PlatformPermission.VIEW_REPORTS,
                        PlatformPermission.CREATE_EXPERIMENT,
                        PlatformPermission.PAUSE_EXPERIMENT);
    }

    @Test
    void platformPermission_shouldRespectAllowedRoles() {
        assertThat(PlatformPermission.VIEW_REPORTS.isAllowedFor(PlatformRole.VIEWER))
                .isTrue();
        assertThat(PlatformPermission.VIEW_REPORTS.isAllowedFor(PlatformRole.OPERATOR))
                .isTrue();
        assertThat(PlatformPermission.CREATE_EXPERIMENT.isAllowedFor(PlatformRole.VIEWER))
                .isFalse();
        assertThat(PlatformPermission.CREATE_EXPERIMENT.isAllowedFor(PlatformRole.EXPERIMENTER))
                .isTrue();
        assertThat(PlatformPermission.PAUSE_EXPERIMENT.isAllowedFor(PlatformRole.EXPERIMENTER))
                .isFalse();
        assertThat(PlatformPermission.PAUSE_EXPERIMENT.isAllowedFor(PlatformRole.OPERATOR))
                .isTrue();
    }
}
