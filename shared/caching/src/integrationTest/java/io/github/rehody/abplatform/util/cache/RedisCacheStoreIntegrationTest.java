package io.github.rehody.abplatform.util.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.config.AbstractIntegrationRedisTest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.core.env.Environment;

@DataRedisTest
class RedisCacheStoreIntegrationTest extends AbstractIntegrationRedisTest {

    @Autowired
    private Environment environment;

    private RedissonClient redissonClient;

    private RedisCacheStore<String> redisCacheStore;

    @BeforeEach
    void setUp() {
        String host = environment.getRequiredProperty("spring.data.redis.host");
        int port = Integer.parseInt(environment.getRequiredProperty("spring.data.redis.port"));

        Config config = new Config();
        config.useSingleServer().setAddress("redis://%s:%d".formatted(host, port));

        redissonClient = Redisson.create(config);
        redissonClient.getKeys().flushall();

        redisCacheStore = new RedisCacheStore<>(
                redissonClient,
                new StringCacheCodec(),
                new RedisCacheConfig(
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(2),
                        0.0d,
                        "integration:test:cache",
                        "integration:test:cache:topic"));
    }

    @AfterEach
    void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void writeValueAndReadValue_shouldPersistAndReturnCachedValue() {
        String key = "flag-" + UUID.randomUUID();

        redisCacheStore.writeValue(key, "value-a");
        Optional<String> response = redisCacheStore.readValue(key);

        assertThat(response).contains("value-a");
    }

    @Test
    void writeMissAndHasMiss_shouldStoreAndRecognizeMissMarker() {
        String key = "flag-" + UUID.randomUUID();

        redisCacheStore.writeMiss(key);

        assertThat(redisCacheStore.hasMiss(key)).isTrue();
        assertThat(redisCacheStore.readValue(key)).isEmpty();
    }

    @Test
    void invalidate_shouldDeleteValueAndMissEntries() {
        String key = "flag-" + UUID.randomUUID();

        redisCacheStore.writeValue(key, "value-b");
        redisCacheStore.writeMiss(key);
        redisCacheStore.invalidate(key);

        assertThat(redisCacheStore.readValue(key)).isEmpty();
        assertThat(redisCacheStore.hasMiss(key)).isFalse();
    }

    @Test
    void subscribeAndPublishInvalidation_shouldDeliverKeyToListener() throws Exception {
        String key = "flag-" + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedKey = new AtomicReference<>();

        int listenerId = redisCacheStore.subscribeInvalidation(value -> {
            receivedKey.set(value);
            latch.countDown();
        });

        redisCacheStore.publishInvalidation(key);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedKey.get()).isEqualTo(key);

        redisCacheStore.unsubscribeInvalidation(listenerId);
    }

    private static class StringCacheCodec implements CacheCodec<String> {

        @Override
        public String write(String value) {
            return value;
        }

        @Override
        public String read(String value) {
            return value;
        }
    }
}
