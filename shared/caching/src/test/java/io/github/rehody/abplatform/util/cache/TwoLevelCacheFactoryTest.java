package io.github.rehody.abplatform.util.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TwoLevelCacheFactoryTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RTopic topic;

    @Mock
    private RBucket<String> valueBucket;

    @Mock
    private RBucket<String> missBucket;

    private TwoLevelCache<String> cache;

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.getTopic(anyString())).thenReturn(topic);
        lenient().when(topic.addListener(eq(String.class), any())).thenReturn(17);
        lenient().when(redissonClient.getBucket(anyString())).thenAnswer(invocation -> {
            String redisKey = invocation.getArgument(0);
            return resolveBucket(redisKey);
        });

        cache = TwoLevelCacheFactory.create(redissonClient, new ObjectMapper(), properties(), String.class);
    }

    @Test
    void create_shouldBuildWorkingCacheWithConfiguredStoreCodecAndConfigs() {
        AtomicInteger loaderCalls = new AtomicInteger();
        Supplier<Optional<String>> loader = () -> {
            loaderCalls.incrementAndGet();
            return Optional.of("value-a");
        };

        Optional<String> first = cache.getOrLoad("flag-a", loader);
        Optional<String> second = cache.getOrLoad("flag-a", loader);

        assertThat(first).contains("value-a");
        assertThat(second).contains("value-a");
        assertThat(loaderCalls.get()).isEqualTo(1);
        verify(valueBucket, times(1)).set(anyString(), any(Duration.class));
        verify(missBucket).delete();
    }

    @Test
    void startAndStop_shouldUseConfiguredInvalidationTopic() {
        cache.start();
        cache.stop();

        verify(topic).addListener(eq(String.class), any());
        verify(topic).removeListener(17);
    }

    @Test
    void stop_shouldSkipUnsubscribeWhenCacheWasNotStarted() {
        cache.stop();

        verify(topic, never()).removeListener(anyInt());
    }

    private TwoLevelCacheProperties properties() {
        return new TestTwoLevelCacheProperties(
                Duration.ofSeconds(10),
                Duration.ofSeconds(5),
                100,
                100,
                Duration.ofMinutes(2),
                Duration.ofSeconds(30),
                0.1d,
                "ab-platform:test:cache",
                "ab-platform:test:cache:invalidation");
    }

    @SuppressWarnings("unchecked")
    private <T> RBucket<T> resolveBucket(String redisKey) {
        return (RBucket<T>) (redisKey.contains("miss:") ? missBucket : valueBucket);
    }

    private record TestTwoLevelCacheProperties(
            Duration l1ValueTtl,
            Duration l1MissTtl,
            long l1ValueSize,
            long l1MissSize,
            Duration l2ValueTtl,
            Duration l2MissTtl,
            double ttlSpread,
            String redisKeyPrefix,
            String invalidationTopic)
            implements TwoLevelCacheProperties {

        @Override
        public Duration getL1ValueTtl() {
            return l1ValueTtl;
        }

        @Override
        public Duration getL1MissTtl() {
            return l1MissTtl;
        }

        @Override
        public long getL1ValueSize() {
            return l1ValueSize;
        }

        @Override
        public long getL1MissSize() {
            return l1MissSize;
        }

        @Override
        public Duration getL2ValueTtl() {
            return l2ValueTtl;
        }

        @Override
        public Duration getL2MissTtl() {
            return l2MissTtl;
        }

        @Override
        public double getTtlSpread() {
            return ttlSpread;
        }

        @Override
        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        @Override
        public String getInvalidationTopic() {
            return invalidationTopic;
        }
    }
}
