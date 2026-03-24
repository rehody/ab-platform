package io.github.rehody.abplatform.util.lock;

public class LockObtainingException extends RuntimeException {

    public LockObtainingException(String message) {
        super(message);
    }

    public LockObtainingException(String message, Throwable cause) {
        super(message, cause);
    }
}
