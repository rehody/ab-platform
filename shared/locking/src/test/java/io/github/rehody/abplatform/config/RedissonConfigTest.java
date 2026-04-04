package io.github.rehody.abplatform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.test.util.ReflectionTestUtils;

class RedissonConfigTest {

    private final RedissonConfig redissonConfig = new RedissonConfig();

    @Test
    void buildAddress_shouldReturnPlainRedisUrlAndHandleBlankPassword() {
        String address = ReflectionTestUtils.invokeMethod(redissonConfig, "buildAddress", "localhost", 6379, "   ");

        assertThat(address).isEqualTo("redis://localhost:6379");
    }

    @Test
    void buildAddress_shouldReturnPlainRedisUrlAndHandleNullPassword() {
        String address = ReflectionTestUtils.invokeMethod(redissonConfig, "buildAddress", "localhost", 6379, null);

        assertThat(address).isEqualTo("redis://localhost:6379");
    }

    @Test
    void buildAddress_shouldEncodePasswordAndReplacePlusWithPercentTwenty() {
        String address =
                ReflectionTestUtils.invokeMethod(redissonConfig, "buildAddress", "redis.local", 6380, "p@ss word:/?");

        assertThat(address).isEqualTo("redis://:p%40ss%20word%3A%2F%3F@redis.local:6380");
    }

    @Test
    void redissonClient_shouldCreateClientAndUseAddressWhenConfigurationProvided() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        Config[] capturedConfig = new Config[1];

        try (MockedStatic<Redisson> redisson = mockStatic(Redisson.class)) {
            redisson.when(() -> Redisson.create(any(Config.class))).thenAnswer(invocation -> {
                capturedConfig[0] = invocation.getArgument(0);
                return redissonClient;
            });

            RedissonClient result = redissonConfig.redissonClient("localhost", 6379, "");

            assertThat(result).isSameAs(redissonClient);
            assertThat(capturedConfig[0]).isNotNull();
            assertThat(capturedConfig[0].useSingleServer().getAddress()).isEqualTo("redis://localhost:6379");
        }
    }
}
