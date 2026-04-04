package io.github.rehody.abplatform.conflict.model;

import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.conflict.enums.ExperimentConflictType;
import io.github.rehody.abplatform.enums.ExperimentState;
import java.util.List;
import java.util.UUID;

public record ExperimentConflict(
        UUID experimentId,
        ExperimentState state,
        String flagKey,
        String domain,
        List<ExperimentConflictType> conflictTypes,
        ConflictSeverity severity) {

    public ExperimentConflict {
        conflictTypes = List.copyOf(conflictTypes);
    }
}
