package io.github.rehody.abplatform.metric.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.cache.MetricDefinitionCache;
import io.github.rehody.abplatform.exception.MetricDefinitionAlreadyExistsException;
import io.github.rehody.abplatform.exception.MetricDefinitionNotFoundException;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.repository.MetricDefinitionRepository;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.service.ActionExecutorService;
import io.github.rehody.abplatform.service.AuditService;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricDefinitionServiceTest {

    @Mock
    private MetricDefinitionCache metricDefinitionCache;

    @Mock
    private MetricDefinitionRepository metricDefinitionRepository;

    @Mock
    private LockExecutor lockExecutor;

    @Mock
    private AuditService auditService;

    private MetricDefinitionService metricDefinitionService;

    @BeforeEach
    void setUp() {
        metricDefinitionService = new MetricDefinitionService(
                metricDefinitionCache,
                metricDefinitionRepository,
                lockExecutor,
                new ActionExecutorService(),
                auditService);
        lenient()
                .when(lockExecutor.withLock(any(LockNamespace.class), anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
    }

    @Test
    void create_shouldSaveMetricAndInvalidateCache() {
        when(metricDefinitionRepository.existsByKey("orders")).thenReturn(false);

        MetricDefinition response = metricDefinitionService.create(
                AuditActor.user(UUID.randomUUID()),
                "orders",
                "Orders",
                MetricType.COUNTABLE,
                MetricDirection.MORE_IS_BETTER,
                MetricSeverity.HIGH,
                new BigDecimal("0.10"));

        assertThat(response.key()).isEqualTo("orders");
        verify(metricDefinitionRepository).save(response);
        verify(metricDefinitionCache).invalidate("orders");
    }

    @Test
    void create_shouldRejectDuplicateMetricKey() {
        when(metricDefinitionRepository.existsByKey("orders")).thenReturn(true);

        assertThatThrownBy(() -> metricDefinitionService.create(
                        AuditActor.user(UUID.randomUUID()),
                        "orders",
                        "Orders",
                        MetricType.COUNTABLE,
                        MetricDirection.MORE_IS_BETTER,
                        MetricSeverity.HIGH,
                        new BigDecimal("0.10")))
                .isInstanceOf(MetricDefinitionAlreadyExistsException.class)
                .hasMessage("Metric definition 'orders' already exists");

        verify(metricDefinitionRepository, never()).save(any());
    }

    @Test
    void update_shouldPersistUpdatedMetricAndInvalidateCache() {
        MetricDefinition current = metricDefinition();
        when(metricDefinitionCache.getOrLoad(eq("orders"), any())).thenReturn(Optional.of(current));

        MetricDefinition response = metricDefinitionService.update(
                AuditActor.user(UUID.randomUUID()),
                "orders",
                "Orders 2",
                MetricType.UNIQUE,
                MetricDirection.LESS_IS_BETTER,
                MetricSeverity.MEDIUM,
                new BigDecimal("0.15"));

        assertThat(response.id()).isEqualTo(current.id());
        assertThat(response.type()).isEqualTo(MetricType.UNIQUE);
        verify(metricDefinitionRepository).update(response);
        verify(metricDefinitionCache).invalidate("orders");
    }

    @Test
    void getByKey_shouldReturnMetricFromCache() {
        MetricDefinition metricDefinition = metricDefinition();
        when(metricDefinitionCache.getOrLoad(eq("orders"), any())).thenReturn(Optional.of(metricDefinition));

        MetricDefinition response = metricDefinitionService.getByKey("orders");

        assertThat(response).isEqualTo(metricDefinition);
    }

    @Test
    void getByKey_shouldThrowWhenMetricMissing() {
        when(metricDefinitionCache.getOrLoad(eq("orders"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> metricDefinitionService.getByKey("orders"))
                .isInstanceOf(MetricDefinitionNotFoundException.class)
                .hasMessage("Metric definition 'orders' not found");
    }

    @Test
    void getAll_shouldDelegateToRepository() {
        List<MetricDefinition> metricDefinitions = List.of(metricDefinition());
        when(metricDefinitionRepository.findAll()).thenReturn(metricDefinitions);

        assertThat(metricDefinitionService.getAll()).isEqualTo(metricDefinitions);
    }

    private MetricDefinition metricDefinition() {
        return new MetricDefinition(
                UUID.randomUUID(),
                "orders",
                "Orders",
                MetricType.COUNTABLE,
                MetricDirection.MORE_IS_BETTER,
                MetricSeverity.HIGH,
                new BigDecimal("0.10"));
    }
}
