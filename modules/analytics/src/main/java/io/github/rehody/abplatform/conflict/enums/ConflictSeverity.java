package io.github.rehody.abplatform.conflict.enums;

public enum ConflictSeverity {
    NONE,
    WARNING,
    BLOCKING;

    public boolean isBlocking() {
        return this == BLOCKING;
    }

    public boolean isNone() {
        return this == NONE;
    }

    public boolean isWarning() {
        return this == WARNING;
    }
}
