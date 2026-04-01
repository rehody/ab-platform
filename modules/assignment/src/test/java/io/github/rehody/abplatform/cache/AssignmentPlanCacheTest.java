package io.github.rehody.abplatform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.rehody.abplatform.service.snapshot.BucketRange;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshot;
import java.time.Duration;
import java.util.List;
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
class AssignmentPlanCacheTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RTopic topic;

    @Mock
    private RBucket<String> valueBucket;

    @Mock
    private RBucket<String> missBucket;

    private AssignmentPlanCache assignmentPlanCache;

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.getTopic(anyString())).thenReturn(topic);
        lenient().when(topic.addListener(eq(String.class), any())).thenReturn(17);
        lenient().when(redissonClient.getBucket(anyString())).thenAnswer(invocation -> {
            String redisKey = invocation.getArgument(0);
            return resolveBucket(redisKey);
        });

        assignmentPlanCache = new AssignmentPlanCache(redissonClient, new ObjectMapper(), properties());
    }

    @Test
    void subscribeInvalidationTopic_shouldSubscribeAndRegisterListenerId() {
        assignmentPlanCache.subscribeInvalidationTopic();

        verify(topic).addListener(eq(String.class), any());
    }

    @Test
    void unsubscribeInvalidationTopic_shouldDoNothingWhenCacheWasNotSubscribed() {
        assignmentPlanCache.unsubscribeInvalidationTopic();

        verify(topic, never()).removeListener(anyInt());
    }

    @Test
    void unsubscribeInvalidationTopic_shouldRemoveRegisteredListenerWhenSubscribed() {
        assignmentPlanCache.subscribeInvalidationTopic();

        assignmentPlanCache.unsubscribeInvalidationTopic();

        verify(topic).removeListener(17);
    }

    @Test
    void getOrLoad_shouldReturnLoadedSnapshotAndReuseL1CacheOnSecondCall() {
        VariantAllocationSnapshot snapshot = snapshot();
        AtomicInteger loaderCalls = new AtomicInteger();
        Supplier<Optional<VariantAllocationSnapshot>> loader = () -> {
            loaderCalls.incrementAndGet();
            return Optional.of(snapshot);
        };

        Optional<VariantAllocationSnapshot> first = assignmentPlanCache.getOrLoad("plan-a", loader);
        Optional<VariantAllocationSnapshot> second = assignmentPlanCache.getOrLoad("plan-a", loader);

        assertThat(first).contains(snapshot);
        assertThat(second).contains(snapshot);
        assertThat(loaderCalls.get()).isEqualTo(1);
    }

    @Test
    void getOrLoad_shouldCacheMissAndSkipLoaderAfterFirstMiss() {
        AtomicInteger loaderCalls = new AtomicInteger();
        Supplier<Optional<VariantAllocationSnapshot>> loader = () -> {
            loaderCalls.incrementAndGet();
            return Optional.empty();
        };

        Optional<VariantAllocationSnapshot> first = assignmentPlanCache.getOrLoad("plan-b", loader);
        Optional<VariantAllocationSnapshot> second = assignmentPlanCache.getOrLoad("plan-b", loader);

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        assertThat(loaderCalls.get()).isEqualTo(1);
        verify(missBucket).set(eq("1"), any(Duration.class));
    }

    private VariantAllocationSnapshot snapshot() {
        return new VariantAllocationSnapshot(List.of(new BucketRange(
                0, 10000, io.github.rehody.abplatform.support.AssignmentFixtures.variant(0, "control", "blue", 1))));
    }

    private AssignmentPlanCacheProperties properties() {
        AssignmentPlanCacheProperties properties = new AssignmentPlanCacheProperties();
        properties.setL1ValueTtl(Duration.ofSeconds(10));
        properties.setL1MissTtl(Duration.ofSeconds(5));
        properties.setL1ValueSize(100);
        properties.setL1MissSize(100);
        properties.setL2ValueTtl(Duration.ofMinutes(2));
        properties.setL2MissTtl(Duration.ofSeconds(30));
        properties.setTtlSpread(0.1d);
        properties.setRedisKeyPrefix("ab-platform:test:assignment-plan-cache");
        properties.setInvalidationTopic("ab-platform:test:assignment-plan-cache:invalidation");
        return properties;
    }

    @SuppressWarnings("unchecked")
    private <T> RBucket<T> resolveBucket(String redisKey) {
        return (RBucket<T>) (redisKey.contains("miss:") ? missBucket : valueBucket);
    }
}
