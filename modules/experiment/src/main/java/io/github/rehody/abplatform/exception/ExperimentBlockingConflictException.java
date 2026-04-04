package io.github.rehody.abplatform.exception;

import java.util.List;

public class ExperimentBlockingConflictException extends RuntimeException {

    private final List<String> conflictingExperimentIds;

    public ExperimentBlockingConflictException(String message, List<String> conflictingExperimentIds) {
        super(message);
        this.conflictingExperimentIds = List.copyOf(conflictingExperimentIds);
    }

    public List<String> conflictingExperimentIds() {
        return conflictingExperimentIds;
    }
}
