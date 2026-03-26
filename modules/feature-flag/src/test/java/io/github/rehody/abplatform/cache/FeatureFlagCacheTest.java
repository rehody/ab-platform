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

import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
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
class FeatureFlagCacheTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RTopic topic;

    @Mock
    private RBucket<String> valueBucket;

    @Mock
    private RBucket<String> missBucket;

    private FeatureFlagCache cache;

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.getTopic(anyString())).thenReturn(topic);
        lenient().when(topic.addListener(eq(String.class), any())).thenReturn(17);
        lenient().when(redissonClient.getBucket(anyString())).thenAnswer(invocation -> {
            String redisKey = invocation.getArgument(0);
            return resolveBucket(redisKey);
        });

        cache = new FeatureFlagCache(redissonClient, new ObjectMapper(), properties());
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
        cache.invalidate("  checkout-redesign  ");

        verify(valueBucket, atLeastOnce()).delete();
        verify(missBucket, atLeastOnce()).delete();
        verify(topic).publish("checkout-redesign");
    }

    @Test
    void getOrLoad_shouldReturnLoadedValueAndReuseL1CacheOnSecondCall() {
        FeatureFlagResponse response =
                new FeatureFlagResponse("flag-a", new FeatureValue(true, FeatureValueType.BOOL), 0L);
        AtomicInteger loaderCalls = new AtomicInteger();
        Supplier<Optional<FeatureFlagResponse>> loader = () -> {
            loaderCalls.incrementAndGet();
            return Optional.of(response);
        };

        Optional<FeatureFlagResponse> first = cache.getOrLoad("flag-a", loader);
        Optional<FeatureFlagResponse> second = cache.getOrLoad("flag-a", loader);

        assertThat(first).contains(response);
        assertThat(second).contains(response);
        assertThat(loaderCalls.get()).isEqualTo(1);
    }

    @Test
    void getOrLoad_shouldCacheMissAndSkipLoaderAfterFirstMiss() {
        AtomicInteger loaderCalls = new AtomicInteger();
        Supplier<Optional<FeatureFlagResponse>> loader = () -> {
            loaderCalls.incrementAndGet();
            return Optional.empty();
        };

        Optional<FeatureFlagResponse> first = cache.getOrLoad("flag-b", loader);
        Optional<FeatureFlagResponse> second = cache.getOrLoad("flag-b", loader);

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        assertThat(loaderCalls.get()).isEqualTo(1);
        verify(missBucket).set(eq("1"), any(Duration.class));
    }

    private FeatureFlagCacheProperties properties() {
        FeatureFlagCacheProperties properties = new FeatureFlagCacheProperties();
        properties.setL1ValueTtl(Duration.ofSeconds(10));
        properties.setL1MissTtl(Duration.ofSeconds(5));
        properties.setL1ValueSize(100);
        properties.setL1MissSize(100);
        properties.setL2ValueTtl(Duration.ofMinutes(2));
        properties.setL2MissTtl(Duration.ofSeconds(30));
        properties.setTtlSpread(0.1d);
        properties.setRedisKeyPrefix("ab-platform:test:cache");
        properties.setInvalidationTopic("ab-platform:test:cache:invalidation");
        return properties;
    }

    @SuppressWarnings("unchecked")
    private <T> RBucket<T> resolveBucket(String redisKey) {
        return (RBucket<T>) (redisKey.contains("miss:") ? missBucket : valueBucket);
    }
}
