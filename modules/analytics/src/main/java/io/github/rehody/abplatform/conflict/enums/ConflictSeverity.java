package io.github.rehody.abplatform.conflict.enums;

public enum ConflictSeverity {
    NONE,
    WARNING,
    BLOCKING;

    public boolean isBlocking() {
        return this == BLOCKING;
    }
}
