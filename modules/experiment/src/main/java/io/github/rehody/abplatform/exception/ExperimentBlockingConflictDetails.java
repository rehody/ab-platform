package io.github.rehody.abplatform.exception;

import io.github.rehody.abplatform.enums.ExperimentState;
import java.util.List;
import java.util.UUID;

public record ExperimentBlockingConflictDetails(
        UUID experimentId,
        ExperimentState state,
        String flagKey,
        String domainKey,
        List<String> conflictTypes,
        String severity) {

    public ExperimentBlockingConflictDetails {
        conflictTypes = List.copyOf(conflictTypes);
    }
}
