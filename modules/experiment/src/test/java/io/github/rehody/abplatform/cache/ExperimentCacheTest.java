package io.github.rehody.abplatform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
class ExperimentCacheTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RTopic topic;

    @Mock
    private RBucket<String> valueBucket;

    @Mock
    private RBucket<String> missBucket;

    private ExperimentCache cache;

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.getTopic(anyString())).thenReturn(topic);
        lenient().when(topic.addListener(eq(String.class), any())).thenReturn(17);
        lenient().when(redissonClient.getBucket(anyString())).thenAnswer(invocation -> {
            String redisKey = invocation.getArgument(0);
            return resolveBucket(redisKey);
        });

        cache = new ExperimentCache(redissonClient, new ObjectMapper(), properties());
    }

    @Test
    void subscribeInvalidationTopic_shouldSubscribeAndRegisterListenerId() {
        cache.subscribeInvalidationTopic();

        verify(topic).addListener(eq(String.class), any());
    }

    @Test
    void unsubscribeInvalidationTopic_shouldDoNothingAndSkipRemoveWhenNotSubscribed() {
        cache.unsubscribeInvalidationTopic();

        verify(topic, never()).removeListener(anyInt());
    }

    @Test
    void unsubscribeInvalidationTopic_shouldRemoveRegisteredListenerAndUnsubscribeWhenSubscribed() {
        cache.subscribeInvalidationTopic();

        cache.unsubscribeInvalidationTopic();

        verify(topic).removeListener(17);
    }

    @Test
    void invalidate_shouldTrimKeyAndClearAllCacheLayers() {
        cache.invalidate("  experiment-flag  ");

        verify(valueBucket, atLeastOnce()).delete();
        verify(missBucket, atLeastOnce()).delete();
        verify(topic).publish("experiment-flag");
    }

    @Test
    void getOrLoad_shouldReturnLoadedValueAndReuseL1CacheOnSecondCall() {
        Experiment experiment = experiment("flag-a");
        AtomicInteger loaderCalls = new AtomicInteger();
        Supplier<Optional<Experiment>> loader = () -> {
            loaderCalls.incrementAndGet();
            return Optional.of(experiment);
        };

        Optional<Experiment> first = cache.getOrLoad("flag-a", loader);
        Optional<Experiment> second = cache.getOrLoad("flag-a", loader);

        assertThat(first).contains(experiment);
        assertThat(second).contains(experiment);
        assertThat(loaderCalls.get()).isEqualTo(1);
    }

    @Test
    void getOrLoad_shouldCacheMissAndSkipLoaderAfterFirstMiss() {
        AtomicInteger loaderCalls = new AtomicInteger();
        Supplier<Optional<Experiment>> loader = () -> {
            loaderCalls.incrementAndGet();
            return Optional.empty();
        };

        Optional<Experiment> first = cache.getOrLoad("flag-b", loader);
        Optional<Experiment> second = cache.getOrLoad("flag-b", loader);

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        assertThat(loaderCalls.get()).isEqualTo(1);
        verify(missBucket).set(eq("1"), any(Duration.class));
    }

    private Experiment experiment(String flagKey) {
        return new Experiment(
                UUID.randomUUID(),
                flagKey,
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE,
                        ExperimentVariantType.CONTROL)),
                ExperimentState.RUNNING,
                0L,
                null,
                null);
    }

    private ExperimentCacheProperties properties() {
        ExperimentCacheProperties properties = new ExperimentCacheProperties();
        properties.setL1ValueTtl(Duration.ofSeconds(10));
        properties.setL1MissTtl(Duration.ofSeconds(5));
        properties.setL1ValueSize(100);
        properties.setL1MissSize(100);
        properties.setL2ValueTtl(Duration.ofMinutes(2));
        properties.setL2MissTtl(Duration.ofSeconds(30));
        properties.setTtlSpread(0.1d);
        properties.setRedisKeyPrefix("ab-platform:test:experiment-cache");
        properties.setInvalidationTopic("ab-platform:test:experiment-cache:invalidation");
        return properties;
    }

    @SuppressWarnings("unchecked")
    private <T> RBucket<T> resolveBucket(String redisKey) {
        return (RBucket<T>) (redisKey.contains("miss:") ? missBucket : valueBucket);
    }
}
