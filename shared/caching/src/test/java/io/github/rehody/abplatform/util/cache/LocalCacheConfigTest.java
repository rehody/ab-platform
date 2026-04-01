package io.github.rehody.abplatform.util.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LocalCacheConfigTest {

    @Test
    void constructor_shouldCreateConfigAndAcceptValidArguments() {
        LocalCacheConfig config = new LocalCacheConfig(Duration.ofSeconds(30), Duration.ofSeconds(5), 100, 50);

        assertThat(config.valueTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.missTtl()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.valueSize()).isEqualTo(100);
        assertThat(config.missSize()).isEqualTo(50);
    }

    @Test
    void constructor_shouldThrowIllegalArgumentExceptionAndRejectNullValueTtl() {
        assertThatThrownBy(() -> new LocalCacheConfig(null, Duration.ofSeconds(5), 100, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("valueTtl is required");
    }

    @Test
    void constructor_shouldThrowIllegalArgumentExceptionAndRejectNullMissTtl() {
        assertThatThrownBy(() -> new LocalCacheConfig(Duration.ofSeconds(30), null, 100, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("missTtl is required");
    }

    @Test
    void constructor_shouldThrowIllegalArgumentExceptionAndRejectNonPositiveValueSize() {
        assertThatThrownBy(() -> new LocalCacheConfig(Duration.ofSeconds(30), Duration.ofSeconds(5), 0, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("valueSize must be positive");
    }

    @Test
    void constructor_shouldThrowIllegalArgumentExceptionAndRejectNonPositiveMissSize() {
        assertThatThrownBy(() -> new LocalCacheConfig(Duration.ofSeconds(30), Duration.ofSeconds(5), 100, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("missSize must be positive");
    }
}
