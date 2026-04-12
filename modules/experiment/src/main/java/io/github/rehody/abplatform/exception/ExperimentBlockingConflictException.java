package io.github.rehody.abplatform.exception;

import java.util.List;

public class ExperimentBlockingConflictException extends RuntimeException {

    private final List<ExperimentBlockingConflictDetails> conflicts;

    public ExperimentBlockingConflictException(String message, List<ExperimentBlockingConflictDetails> conflicts) {
        super(message);
        this.conflicts = List.copyOf(conflicts);
    }

    public List<ExperimentBlockingConflictDetails> conflicts() {
        return conflicts;
    }
}
