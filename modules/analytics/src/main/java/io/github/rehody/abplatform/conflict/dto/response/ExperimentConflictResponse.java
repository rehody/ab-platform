package io.github.rehody.abplatform.conflict.dto.response;

import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.conflict.enums.ExperimentConflictType;
import io.github.rehody.abplatform.conflict.model.ExperimentConflict;
import io.github.rehody.abplatform.enums.ExperimentState;
import java.util.List;
import java.util.UUID;

public record ExperimentConflictResponse(
        UUID experimentId,
        ExperimentState state,
        String flagKey,
        String domain,
        List<ExperimentConflictType> conflictTypes,
        ConflictSeverity severity) {
    public static ExperimentConflictResponse from(ExperimentConflict conflict) {
        return new ExperimentConflictResponse(
                conflict.experimentId(),
                conflict.state(),
                conflict.flagKey(),
                conflict.domain(),
                conflict.conflictTypes(),
                conflict.severity());
    }
}
