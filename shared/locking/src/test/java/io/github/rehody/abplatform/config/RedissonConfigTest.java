package io.github.rehody.abplatform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

class RedissonConfigTest {

    private final RedissonConfig redissonConfig = new RedissonConfig();
    private RedissonClient redissonClient;

    @AfterEach
    void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

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
        redissonClient = redissonConfig.redissonClient("localhost", 6379, "");

        assertThat(redissonClient.getConfig().useSingleServer().getAddress()).isEqualTo("redis://localhost:6379");
    }
}
