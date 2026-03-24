package io.github.rehody.abplatform.exception;

public class FeatureFlagNotFoundException extends RuntimeException {
    public FeatureFlagNotFoundException(String message) {
        super(message);
    }
}
