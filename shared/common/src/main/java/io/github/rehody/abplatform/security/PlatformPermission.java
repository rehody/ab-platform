package io.github.rehody.abplatform.security;

import java.util.EnumSet;
import java.util.Set;

public enum PlatformPermission {
    VIEW_EXPERIMENTS(viewerAndAbove()),
    VIEW_EXPERIMENT_HISTORY(viewerAndAbove()),
    VIEW_ROLLOUT_STATE(viewerAndAbove()),
    VIEW_CONFLICTS(viewerAndAbove()),
    VIEW_REPORTS(viewerAndAbove()),
    VIEW_FEATURE_FLAGS(viewerAndAbove()),
    VIEW_METRIC_DEFINITIONS(viewerAndAbove()),
    VIEW_EXPERIMENT_METRIC_BINDINGS(viewerAndAbove()),

    CREATE_EXPERIMENT(experimenterAndAbove()),
    UPDATE_EXPERIMENT_DRAFT(experimenterAndAbove()),
    SUBMIT_EXPERIMENT_FOR_REVIEW(experimenterAndAbove()),
    CREATE_FEATURE_FLAG(experimenterAndAbove()),
    UPDATE_FEATURE_FLAG(experimenterAndAbove()),

    APPROVE_EXPERIMENT(operatorOnly()),
    REJECT_EXPERIMENT(operatorOnly()),
    START_EXPERIMENT(operatorOnly()),
    PAUSE_EXPERIMENT(operatorOnly()),
    RESUME_EXPERIMENT(operatorOnly()),
    COMPLETE_EXPERIMENT(operatorOnly()),
    ARCHIVE_EXPERIMENT(operatorOnly()),
    ADVANCE_EXPERIMENT_ROLLOUT(operatorOnly()),
    ROLLBACK_EXPERIMENT_ROLLOUT(operatorOnly()),
    UPDATE_EXPERIMENT_METRIC_BINDINGS(operatorOnly()),
    RESOLVE_EXPERIMENT_RISK(operatorOnly()),
    CREATE_METRIC_DEFINITION(operatorOnly()),
    UPDATE_METRIC_DEFINITION(operatorOnly());

    private final Set<PlatformRole> allowedRoles;

    PlatformPermission(Set<PlatformRole> allowedRoles) {
        this.allowedRoles = Set.copyOf(allowedRoles);
    }

    public boolean isAllowedFor(PlatformRole role) {
        return allowedRoles.contains(role);
    }

    private static Set<PlatformRole> viewerAndAbove() {
        return EnumSet.of(PlatformRole.VIEWER, PlatformRole.EXPERIMENTER, PlatformRole.OPERATOR);
    }

    private static Set<PlatformRole> experimenterAndAbove() {
        return EnumSet.of(PlatformRole.EXPERIMENTER, PlatformRole.OPERATOR);
    }

    private static Set<PlatformRole> operatorOnly() {
        return EnumSet.of(PlatformRole.OPERATOR);
    }
}
