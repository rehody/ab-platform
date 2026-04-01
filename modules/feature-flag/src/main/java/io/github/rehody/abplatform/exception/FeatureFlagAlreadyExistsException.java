package io.github.rehody.abplatform.exception;

public class FeatureFlagAlreadyExistsException extends RuntimeException {
    public FeatureFlagAlreadyExistsException(String message) {
        super(message);
    }
}
