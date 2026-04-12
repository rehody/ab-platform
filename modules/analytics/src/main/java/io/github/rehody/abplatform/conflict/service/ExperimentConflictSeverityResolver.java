package io.github.rehody.abplatform.conflict.service;

import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.enums.ExperimentState;
import org.springframework.stereotype.Component;

@Component
public class ExperimentConflictSeverityResolver {

    public ConflictSeverity resolve(ExperimentState state) {
        return switch (state) {
            case RUNNING -> ConflictSeverity.BLOCKING;
            case DRAFT, REJECTED, IN_REVIEW, APPROVED, PAUSED -> ConflictSeverity.WARNING;
            case COMPLETED, ARCHIVED -> ConflictSeverity.NONE;
        };
    }
}
