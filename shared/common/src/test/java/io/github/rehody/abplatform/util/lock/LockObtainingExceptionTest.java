package io.github.rehody.abplatform.util.lock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LockObtainingExceptionTest {

    @Test
    void constructor_shouldSetMessageAndKeepCauseNullWhenMessageOnlyProvided() {
        LockObtainingException exception = new LockObtainingException("timeout");

        assertThat(exception.getMessage()).isEqualTo("timeout");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void constructor_shouldSetMessageAndCauseWhenBothProvided() {
        RuntimeException cause = new RuntimeException("boom");

        LockObtainingException exception = new LockObtainingException("interrupted", cause);

        assertThat(exception.getMessage()).isEqualTo("interrupted");
        assertThat(exception.getCause()).isSameAs(cause);
    }
}
