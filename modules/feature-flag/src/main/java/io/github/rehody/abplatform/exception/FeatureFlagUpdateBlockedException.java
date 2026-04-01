package io.github.rehody.abplatform.exception;

public class FeatureFlagUpdateBlockedException extends RuntimeException {
    public FeatureFlagUpdateBlockedException(String message) {
        super(message);
    }
}
