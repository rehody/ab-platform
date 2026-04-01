package io.github.rehody.abplatform.util.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TwoLevelCacheTest {

    @Mock
    private CacheStore<String> cacheStore;

    private TwoLevelCache<String> twoLevelCache;

    @BeforeEach
    void setUp() {
        twoLevelCache = new TwoLevelCache<>(
                cacheStore, new LocalCacheConfig(Duration.ofMinutes(1), Duration.ofSeconds(10), 100, 100));
        lenient().when(cacheStore.readValue(anyString())).thenReturn(Optional.empty());
        lenient().when(cacheStore.hasMiss(anyString())).thenReturn(false);
    }

    @Test
    void startAndStop_shouldSubscribeAndUnsubscribeInvalidationListener() {
        when(cacheStore.subscribeInvalidation(any())).thenReturn(9);

        twoLevelCache.start();
        twoLevelCache.stop();

        verify(cacheStore).subscribeInvalidation(any());
        verify(cacheStore).unsubscribeInvalidation(9);
    }

    @Test
    void stop_shouldSkipUnsubscribeAndHandleNotStartedCache() {
        twoLevelCache.stop();

        verify(cacheStore, never()).unsubscribeInvalidation(anyInt());
    }

    @Test
    void invalidate_shouldTrimKeyAndPublishInvalidation() {
        twoLevelCache.invalidate("  flag-a  ");

        verify(cacheStore).invalidate("flag-a");
        verify(cacheStore).publishInvalidation("flag-a");
    }

    @Test
    void getOrLoad_shouldReturnValueAndUseL1CacheAfterFirstLoad() {
        AtomicInteger loaderCalls = new AtomicInteger();
        Supplier<Optional<String>> loader = () -> {
            loaderCalls.incrementAndGet();
            return Optional.of("value-a");
        };

        Optional<String> first = twoLevelCache.getOrLoad("flag-b", loader);
        Optional<String> second = twoLevelCache.getOrLoad("flag-b", loader);

        assertThat(first).contains("value-a");
        assertThat(second).contains("value-a");
        assertThat(loaderCalls.get()).isEqualTo(1);
        verify(cacheStore).writeValue("flag-b", "value-a");
    }

    @Test
    void getOrLoad_shouldReturnValueAndUseL2ValueWithoutSourceLoad() {
        when(cacheStore.readValue("flag-c")).thenReturn(Optional.of("l2-value"));
        AtomicInteger loaderCalls = new AtomicInteger();
        Supplier<Optional<String>> loader = () -> {
            loaderCalls.incrementAndGet();
            return Optional.of("source-value");
        };

        Optional<String> response = twoLevelCache.getOrLoad("flag-c", loader);

        assertThat(response).contains("l2-value");
        assertThat(loaderCalls.get()).isZero();
        verify(cacheStore, never()).writeValue(eq("flag-c"), eq("source-value"));
    }

    @Test
    void getOrLoad_shouldReturnEmptyAndSetL1MissWhenL2ContainsMissMarker() {
        when(cacheStore.hasMiss("flag-d")).thenReturn(true);
        AtomicInteger loaderCalls = new AtomicInteger();
        Supplier<Optional<String>> loader = () -> {
            loaderCalls.incrementAndGet();
            return Optional.of("source-value");
        };

        Optional<String> response = twoLevelCache.getOrLoad("flag-d", loader);

        assertThat(response).isEmpty();
        assertThat(loaderCalls.get()).isZero();
        verify(cacheStore, never()).writeValue(anyString(), anyString());
    }

    @Test
    void getOrLoad_shouldWriteMissMarkerAndSkipLoaderAfterSourceMiss() {
        AtomicInteger loaderCalls = new AtomicInteger();
        Supplier<Optional<String>> loader = () -> {
            loaderCalls.incrementAndGet();
            return Optional.empty();
        };

        Optional<String> first = twoLevelCache.getOrLoad("flag-e", loader);
        Optional<String> second = twoLevelCache.getOrLoad("flag-e", loader);

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        assertThat(loaderCalls.get()).isEqualTo(1);
        verify(cacheStore).writeMiss("flag-e");
    }

    @Test
    void getOrLoad_shouldThrowIllegalArgumentExceptionAndRejectNullKey() {
        assertThatThrownBy(() -> twoLevelCache.getOrLoad(null, Optional::empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cache key cannot be null or blank");
    }

    @Test
    void getOrLoad_shouldThrowIllegalArgumentExceptionAndRejectBlankKey() {
        assertThatThrownBy(() -> twoLevelCache.getOrLoad("   ", Optional::empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cache key cannot be null or blank");
    }
}
