package io.github.rehody.abplatform.exception;

import java.util.List;

public class ExperimentActivationConflictException extends RuntimeException {

    private final List<String> conflictingMetricKeys;

    public ExperimentActivationConflictException(String message, List<String> conflictingMetricKeys) {
        super(message);
        this.conflictingMetricKeys = List.copyOf(conflictingMetricKeys);
    }

    public List<String> conflictingMetricKeys() {
        return conflictingMetricKeys;
    }
}
