package io.github.rehody.abplatform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ServiceActionExecutorTest {

    private final ServiceActionExecutor serviceActionExecutor = new ServiceActionExecutor();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void executeAfterCommit_shouldRunActionImmediatelyWhenSynchronizationInactive() {
        AtomicInteger calls = new AtomicInteger();

        serviceActionExecutor.executeAfterCommit(calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void executeAfterCommit_shouldRegisterActionAndRunAfterCommitWhenSynchronizationActive() {
        AtomicInteger calls = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        serviceActionExecutor.executeAfterCommit(calls::incrementAndGet);

        assertThat(calls.get()).isZero();
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        assertThat(calls.get()).isEqualTo(1);
    }
}
