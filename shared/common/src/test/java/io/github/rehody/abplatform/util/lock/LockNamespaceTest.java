package io.github.rehody.abplatform.util.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LockNamespaceTest {

    @Test
    void of_shouldNormalizeNamespaceAndConvertToLowerCase() {
        LockNamespace namespace = LockNamespace.of("  Feature.Flag-1  ");

        assertThat(namespace.value()).isEqualTo("feature.flag-1");
    }

    @Test
    void of_shouldThrowIllegalArgumentExceptionAndRejectNullNamespace() {
        assertThatThrownBy(() -> LockNamespace.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("namespace is required");
    }

    @Test
    void of_shouldThrowIllegalArgumentExceptionAndRejectBlankNamespace() {
        assertThatThrownBy(() -> LockNamespace.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("namespace is required");
    }

    @Test
    void of_shouldThrowIllegalArgumentExceptionAndRejectInvalidPattern() {
        assertThatThrownBy(() -> LockNamespace.of("feature flag"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace must match pattern");
    }

    @Test
    void of_shouldCreateNamespaceAndAcceptValidDelimiterPattern() {
        LockNamespace namespace = LockNamespace.of("service_a.b-c");

        assertThat(namespace.value()).isEqualTo("service_a.b-c");
    }
}
