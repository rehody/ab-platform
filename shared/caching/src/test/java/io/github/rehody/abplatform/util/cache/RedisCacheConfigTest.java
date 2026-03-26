package io.github.rehody.abplatform.util.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RedisCacheConfigTest {

    @Test
    void constructor_shouldCreateConfigAndAcceptValidArguments() {
        RedisCacheConfig config =
                new RedisCacheConfig(Duration.ofMinutes(5), Duration.ofSeconds(15), 0.25d, "prefix", "topic");

        assertThat(config.valueTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.missTtl()).isEqualTo(Duration.ofSeconds(15));
        assertThat(config.ttlSpread()).isEqualTo(0.25d);
        assertThat(config.redisKeyPrefix()).isEqualTo("prefix");
        assertThat(config.invalidationTopic()).isEqualTo("topic");
    }

    @Test
    void constructor_shouldThrowIllegalArgumentExceptionAndRejectNullValueTtl() {
        assertThatThrownBy(() -> new RedisCacheConfig(null, Duration.ofSeconds(1), 0.1d, "prefix", "topic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("valueTtl is required");
    }

    @Test
    void constructor_shouldThrowIllegalArgumentExceptionAndRejectNullMissTtl() {
        assertThatThrownBy(() -> new RedisCacheConfig(Duration.ofSeconds(1), null, 0.1d, "prefix", "topic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("missTtl is required");
    }

    @Test
    void constructor_shouldThrowIllegalArgumentExceptionAndRejectNullInvalidationTopic() {
        assertThatThrownBy(
                        () -> new RedisCacheConfig(Duration.ofSeconds(1), Duration.ofSeconds(1), 0.1d, "prefix", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalidationTopic is required");
    }

    @Test
    void constructor_shouldThrowIllegalArgumentExceptionAndRejectBlankInvalidationTopic() {
        assertThatThrownBy(
                        () -> new RedisCacheConfig(Duration.ofSeconds(1), Duration.ofSeconds(1), 0.1d, "prefix", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalidationTopic is required");
    }
}
