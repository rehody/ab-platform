package io.github.rehody.abplatform.util.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

@ExtendWith(MockitoExtension.class)
class RedisCacheStoreTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private CacheCodec<String> cacheCodec;

    @Mock
    private RBucket<String> valueBucket;

    @Mock
    private RBucket<String> missBucket;

    @Mock
    private RTopic topic;

    private RedisCacheStore<String> redisCacheStore;

    @BeforeEach
    void setUp() {
        redisCacheStore = new RedisCacheStore<>(
                redissonClient,
                cacheCodec,
                new RedisCacheConfig(Duration.ofSeconds(2), Duration.ofSeconds(1), 0.2d, " ", "  test-topic  "));

        lenient().when(redissonClient.getTopic(anyString())).thenReturn(topic);
        lenient().when(redissonClient.getBucket(anyString())).thenAnswer(invocation -> {
            String redisKey = invocation.getArgument(0);
            return resolveBucket(redisKey);
        });
    }

    @Test
    void writeValue_shouldNormalizePrefixAndClampSpreadWhenConfigurationOutOfRange() throws Exception {
        RedisCacheStore<String> storeWithCustomConfig = new RedisCacheStore<>(
                redissonClient,
                cacheCodec,
                new RedisCacheConfig(Duration.ofSeconds(2), Duration.ofSeconds(1), -3.0d, "custom", "topic"));
        when(cacheCodec.write("value")).thenReturn("encoded");

        storeWithCustomConfig.writeValue("flag-a", "value");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient, atLeastOnce()).getBucket(keyCaptor.capture());
        assertThat(keyCaptor.getAllValues()).contains("custom:value:flag-a");

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueBucket).set(eq("encoded"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue().toMillis()).isGreaterThanOrEqualTo(1_000L);
    }

    @Test
    void writeValue_shouldKeepPrefixAndUseExistingTrailingColonWhenAlreadyNormalized() throws Exception {
        RedisCacheStore<String> storeWithNormalizedPrefix = new RedisCacheStore<>(
                redissonClient,
                cacheCodec,
                new RedisCacheConfig(Duration.ofSeconds(2), Duration.ofSeconds(1), 0.0d, "custom:", "topic"));
        when(cacheCodec.write("value")).thenReturn("encoded");

        storeWithNormalizedPrefix.writeValue("flag-z", "value");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient, atLeastOnce()).getBucket(keyCaptor.capture());
        assertThat(keyCaptor.getAllValues()).contains("custom:value:flag-z");
    }

    @Test
    void writeValue_shouldUseDefaultPrefixAndHandleNullPrefixConfiguration() throws Exception {
        RedisCacheStore<String> storeWithNullPrefix = new RedisCacheStore<>(
                redissonClient,
                cacheCodec,
                new RedisCacheConfig(Duration.ofSeconds(2), Duration.ofSeconds(1), 0.0d, null, "topic"));
        when(cacheCodec.write("value")).thenReturn("encoded");

        storeWithNullPrefix.writeValue("flag-default-prefix", "value");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient, atLeastOnce()).getBucket(keyCaptor.capture());
        assertThat(keyCaptor.getAllValues()).contains("ab-platform:cache:value:flag-default-prefix");
    }

    @Test
    void readValue_shouldReturnEmptyAndHandleNullPayload() {
        when(valueBucket.get()).thenReturn(null);

        Optional<String> response = redisCacheStore.readValue("flag-b");

        assertThat(response).isEmpty();
    }

    @Test
    void readValue_shouldReturnEmptyAndHandleBlankPayload() {
        when(valueBucket.get()).thenReturn("   ");

        Optional<String> response = redisCacheStore.readValue("flag-c");

        assertThat(response).isEmpty();
    }

    @Test
    void readValue_shouldReturnDecodedValueAndDeserializePayload() throws Exception {
        when(valueBucket.get()).thenReturn("encoded");
        when(cacheCodec.read("encoded")).thenReturn("decoded");

        Optional<String> response = redisCacheStore.readValue("flag-d");

        assertThat(response).contains("decoded");
    }

    @Test
    void readValue_shouldReturnEmptyAndDeleteBrokenPayloadWhenDeserializationFails() throws Exception {
        when(valueBucket.get()).thenReturn("broken");
        when(cacheCodec.read("broken")).thenThrow(new Exception("decode-error"));

        Optional<String> response = redisCacheStore.readValue("flag-e");

        assertThat(response).isEmpty();
        verify(valueBucket).delete();
    }

    @Test
    void readValue_shouldReturnEmptyAndIgnoreRedisFailureWhenGetThrows() {
        when(valueBucket.get()).thenThrow(new RuntimeException("redis down"));

        Optional<String> response = redisCacheStore.readValue("flag-f");

        assertThat(response).isEmpty();
    }

    @Test
    void hasMiss_shouldReturnTrueAndRecognizeMissMarker() {
        when(missBucket.get()).thenReturn("1");

        boolean miss = redisCacheStore.hasMiss("flag-g");

        assertThat(miss).isTrue();
    }

    @Test
    void hasMiss_shouldReturnFalseAndHandleRedisFailure() {
        when(missBucket.get()).thenThrow(new RuntimeException("redis down"));

        boolean miss = redisCacheStore.hasMiss("flag-h");

        assertThat(miss).isFalse();
    }

    @Test
    void writeValue_shouldSkipWriteAndHandleSerializationFailure() throws Exception {
        when(cacheCodec.write("value")).thenThrow(new Exception("encode-error"));

        redisCacheStore.writeValue("flag-i", "value");

        verify(valueBucket, never()).set(anyString(), any(Duration.class));
    }

    @Test
    void writeValue_shouldWriteValueAndClearMissMarker() throws Exception {
        when(cacheCodec.write("value")).thenReturn("encoded");

        redisCacheStore.writeValue("flag-j", "value");

        verify(valueBucket).set(eq("encoded"), any(Duration.class));
        verify(missBucket).delete();
    }

    @Test
    void writeMiss_shouldWriteMissMarkerAndDeleteValue() {
        redisCacheStore.writeMiss("flag-k");

        verify(valueBucket).delete();
        verify(missBucket).set(eq("1"), any(Duration.class));
    }

    @Test
    void invalidate_shouldDeleteValueAndMissEntries() {
        redisCacheStore.invalidate("flag-l");

        verify(valueBucket).delete();
        verify(missBucket).delete();
    }

    @Test
    void publishInvalidation_shouldPublishKeyAndUseTopic() {
        redisCacheStore.publishInvalidation("flag-m");

        verify(redissonClient).getTopic("test-topic");
        verify(topic).publish("flag-m");
    }

    @Test
    void subscribeAndUnsubscribeInvalidation_shouldManageTopicListener() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<MessageListener<String>> listenerCaptor = ArgumentCaptor.forClass((Class) MessageListener.class);
        AtomicReference<String> receivedKey = new AtomicReference<>();
        when(topic.addListener(eq(String.class), listenerCaptor.capture())).thenReturn(15);

        int listenerId = redisCacheStore.subscribeInvalidation(receivedKey::set);
        listenerCaptor.getValue().onMessage("test-topic", "flag-n");
        redisCacheStore.unsubscribeInvalidation(listenerId);

        assertThat(listenerId).isEqualTo(15);
        assertThat(receivedKey.get()).isEqualTo("flag-n");
        verify(topic).addListener(eq(String.class), any());
        verify(topic).removeListener(15);
    }

    @Test
    void unsubscribeInvalidation_shouldDelegateToTopicAndRemoveListenerId() {
        redisCacheStore.unsubscribeInvalidation(42);

        verify(topic).removeListener(42);
    }

    @SuppressWarnings("unchecked")
    private <T> RBucket<T> resolveBucket(String redisKey) {
        return (RBucket<T>) (redisKey.contains("miss:") ? missBucket : valueBucket);
    }
}
