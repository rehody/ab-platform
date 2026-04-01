package io.github.rehody.abplatform.util.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RedissonLockExecutorTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private RedissonLockExecutor redissonLockExecutor;

    @BeforeEach
    void setUp() {
        redissonLockExecutor = new RedissonLockExecutor(redissonClient);
        ReflectionTestUtils.setField(redissonLockExecutor, "keyPrefix", "ab-platform:lock");
        ReflectionTestUtils.setField(redissonLockExecutor, "defaultWaitTime", Duration.ofSeconds(3));
        ReflectionTestUtils.setField(redissonLockExecutor, "defaultLeaseTime", Duration.ofSeconds(10));
    }

    @Test
    void withLock_shouldExecuteActionAndUseDefaultWaitAndLeaseTime() throws Exception {
        when(redissonClient.getLock("ab-platform:lock:feature:flag-a")).thenReturn(lock);
        when(lock.tryLock(3_000L, 10_000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = redissonLockExecutor.withLock(LockNamespace.of("feature"), "  flag-a  ", () -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(lock).tryLock(3_000L, 10_000L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void withLock_shouldExecuteActionAndUseProvidedWaitAndLeaseTime() throws Exception {
        when(redissonClient.getLock("ab-platform:lock:feature:flag-b")).thenReturn(lock);
        when(lock.tryLock(200L, 500L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = redissonLockExecutor.withLock(
                LockNamespace.of("feature"), "flag-b", Duration.ofMillis(200), Duration.ofMillis(500), () -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(lock).tryLock(200L, 500L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void withLock_shouldThrowLockObtainingExceptionAndFailWhenLockTimeoutReached() throws Exception {
        when(redissonClient.getLock("ab-platform:lock:feature:flag-c")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        assertThatThrownBy(() -> redissonLockExecutor.withLock(LockNamespace.of("feature"), "flag-c", () -> "ok"))
                .isInstanceOf(LockObtainingException.class)
                .hasMessage("Lock timeout for key 'ab-platform:lock:feature:flag-c'");

        verify(lock, never()).unlock();
    }

    @Test
    void withLock_shouldThrowLockObtainingExceptionAndPreserveInterruptWhenInterrupted() throws Exception {
        when(redissonClient.getLock("ab-platform:lock:feature:flag-d")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("interrupt"));

        assertThatThrownBy(() -> redissonLockExecutor.withLock(LockNamespace.of("feature"), "flag-d", () -> "ok"))
                .isInstanceOf(LockObtainingException.class)
                .hasMessage("Lock interrupted for key 'ab-platform:lock:feature:flag-d'")
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        //noinspection ResultOfMethodCallIgnored
        Thread.interrupted();
    }

    @Test
    void withLock_shouldUnlockAndPropagateExceptionWhenActionFails() throws Exception {
        when(redissonClient.getLock("ab-platform:lock:feature:flag-e")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() -> redissonLockExecutor.withLock(LockNamespace.of("feature"), "flag-e", () -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(lock).unlock();
    }

    @Test
    void withLock_shouldSkipUnlockAndHandleNotHeldLockAfterAction() throws Exception {
        when(redissonClient.getLock("ab-platform:lock:feature:flag-f")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        String response = redissonLockExecutor.withLock(LockNamespace.of("feature"), "flag-f", () -> "done");

        assertThat(response).isEqualTo("done");
        verify(lock, never()).unlock();
    }

    @Test
    void withLock_shouldThrowIllegalArgumentExceptionAndRejectNullNamespace() {
        assertThatThrownBy(() -> redissonLockExecutor.withLock(null, "flag-g", () -> "ok"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("namespace is required");
    }

    @Test
    void withLock_shouldThrowIllegalArgumentExceptionAndRejectBlankKey() {
        assertThatThrownBy(() -> redissonLockExecutor.withLock(LockNamespace.of("feature"), "   ", () -> "ok"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("key is required");
    }

    @Test
    void withLock_shouldThrowIllegalArgumentExceptionAndRejectNullKey() {
        assertThatThrownBy(() -> redissonLockExecutor.withLock(LockNamespace.of("feature"), null, () -> "ok"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("key is required");
    }

    @Test
    void withLock_shouldUseProvidedPrefixAndComposeLockKeyWhenPrefixAlreadyEndsWithColon() throws Exception {
        ReflectionTestUtils.setField(redissonLockExecutor, "keyPrefix", "ab-platform:lock:");
        when(redissonClient.getLock("ab-platform:lock:feature:flag-j")).thenReturn(lock);
        when(lock.tryLock(3_000L, 10_000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = redissonLockExecutor.withLock(LockNamespace.of("feature"), "flag-j", () -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(redissonClient).getLock("ab-platform:lock:feature:flag-j");
        verify(lock).unlock();
    }

    @Test
    void withLock_shouldThrowIllegalArgumentExceptionAndRejectNullPrefix() {
        ReflectionTestUtils.setField(redissonLockExecutor, "keyPrefix", null);

        assertThatThrownBy(() -> redissonLockExecutor.withLock(LockNamespace.of("feature"), "flag-h", () -> "ok"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("keyPrefix is required");
    }

    @Test
    void withLock_shouldThrowIllegalArgumentExceptionAndRejectBlankPrefix() {
        ReflectionTestUtils.setField(redissonLockExecutor, "keyPrefix", "   ");

        assertThatThrownBy(() -> redissonLockExecutor.withLock(LockNamespace.of("feature"), "flag-i", () -> "ok"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("keyPrefix is required");
    }
}
