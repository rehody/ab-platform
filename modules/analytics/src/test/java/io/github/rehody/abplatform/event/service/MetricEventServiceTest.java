package io.github.rehody.abplatform.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.event.model.MetricEvent;
import io.github.rehody.abplatform.event.repository.MetricEventRepository;
import io.github.rehody.abplatform.exception.MetricEventAlreadyExistsException;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.service.MetricDefinitionService;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricEventServiceTest {

    @Mock
    private LockExecutor lockExecutor;

    @Mock
    private MetricDefinitionService metricDefinitionService;

    @Mock
    private MetricEventRepository metricEventRepository;

    private MetricEventService metricEventService;

    @BeforeEach
    void setUp() {
        metricEventService = new MetricEventService(lockExecutor, metricDefinitionService, metricEventRepository);
        lenient()
                .when(lockExecutor.withLock(any(LockNamespace.class), anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
    }

    @Test
    void create_shouldSaveCountableMetricEventWithoutUniqueCheck() {
        UUID userId = UUID.randomUUID();
        when(metricDefinitionService.getByKey("orders")).thenReturn(countableMetricDefinition("orders"));

        MetricEvent response = metricEventService.create(userId, "orders");

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.metricKey()).isEqualTo("orders");
        verify(metricEventRepository).save(response);
    }

    @Test
    void create_shouldSaveUniqueMetricEventUnderLock() {
        UUID userId = UUID.randomUUID();
        when(metricDefinitionService.getByKey("signup")).thenReturn(uniqueMetricDefinition("signup"));
        when(metricEventRepository.existsUniqueEventForUser(userId, "signup")).thenReturn(false);

        MetricEvent response = metricEventService.create(userId, "signup");

        assertThat(response.metricKey()).isEqualTo("signup");
        verify(metricEventRepository).save(response);
    }

    @Test
    void create_shouldRejectDuplicateUniqueMetricEvent() {
        UUID userId = UUID.randomUUID();
        when(metricDefinitionService.getByKey("signup")).thenReturn(uniqueMetricDefinition("signup"));
        when(metricEventRepository.existsUniqueEventForUser(userId, "signup")).thenReturn(true);

        assertThatThrownBy(() -> metricEventService.create(userId, "signup"))
                .isInstanceOf(MetricEventAlreadyExistsException.class)
                .hasMessage("Metric event for user '%s' and metric '%s' already exists".formatted(userId, "signup"));

        verify(metricEventRepository, never()).save(any());
    }

    private MetricDefinition countableMetricDefinition(String key) {
        return new MetricDefinition(
                UUID.randomUUID(),
                key,
                "Orders",
                MetricType.COUNTABLE,
                MetricDirection.MORE_IS_BETTER,
                MetricSeverity.HIGH,
                new BigDecimal("0.10"));
    }

    private MetricDefinition uniqueMetricDefinition(String key) {
        return new MetricDefinition(
                UUID.randomUUID(),
                key,
                "Signup",
                MetricType.UNIQUE,
                MetricDirection.MORE_IS_BETTER,
                MetricSeverity.MEDIUM,
                new BigDecimal("0.20"));
    }
}
