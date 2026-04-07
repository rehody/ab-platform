package io.github.rehody.abplatform.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.binding.repository.ExperimentMetricBindingRepository;
import io.github.rehody.abplatform.conflict.repository.ExperimentConflictRepository;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.event.model.MetricEvent;
import io.github.rehody.abplatform.event.repository.MetricEventRepository;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.repository.MetricDefinitionRepository;
import io.github.rehody.abplatform.metric.repository.rowmapper.MetricDefinitionRowMapper;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.repository.AssignmentEventReportRepository;
import io.github.rehody.abplatform.report.repository.CountableMetricEventReportRepository;
import io.github.rehody.abplatform.report.repository.UniqueMetricEventReportRepository;
import io.github.rehody.abplatform.report.repository.aggregate.AssignmentVariantAggregate;
import io.github.rehody.abplatform.report.repository.aggregate.CountableMetricVariantAggregate;
import io.github.rehody.abplatform.report.repository.aggregate.UniqueMetricVariantAggregate;
import io.github.rehody.abplatform.repository.rowmapper.ExperimentRowMapper;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.risk.repository.ExperimentMetricRiskRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class MetricDefinitionRepositoryTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private MetricDefinitionRowMapper metricDefinitionRowMapper;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<MetricDefinition> mappedQuerySpec;

    @Mock
    private JdbcClient.MappedQuerySpec<Boolean> booleanQuerySpec;

    private MetricDefinitionRepository metricDefinitionRepository;

    @BeforeEach
    void setUp() {
        metricDefinitionRepository = new MetricDefinitionRepository(jdbcClient, metricDefinitionRowMapper);
    }

    @Test
    void shouldFindSaveUpdateAndCheckMetricDefinitions() {
        MetricDefinition metricDefinition = metricDefinition();
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.query(metricDefinitionRowMapper)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.of(metricDefinition));
        when(mappedQuerySpec.list()).thenReturn(List.of(metricDefinition));
        when(statementSpec.query(Boolean.class)).thenReturn(booleanQuerySpec);
        when(booleanQuerySpec.single()).thenReturn(Boolean.TRUE);
        when(statementSpec.update()).thenReturn(1);

        assertThat(metricDefinitionRepository.findByKey("orders")).contains(metricDefinition);
        assertThat(metricDefinitionRepository.findAll()).containsExactly(metricDefinition);

        metricDefinitionRepository.save(metricDefinition);
        assertThat(metricDefinitionRepository.update(metricDefinition)).isEqualTo(1);
        assertThat(metricDefinitionRepository.existsByKey("orders")).isTrue();

        verify(statementSpec, times(2)).param("deviationThreshold", new BigDecimal("0.10"));
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

@ExtendWith(MockitoExtension.class)
class MetricDefinitionRowMapperTest {

    private final MetricDefinitionRowMapper metricDefinitionRowMapper = new MetricDefinitionRowMapper();

    @Test
    void shouldMapMetricDefinitionRow() throws Exception {
        UUID id = UUID.randomUUID();
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("id", UUID.class)).thenReturn(id);
        when(resultSet.getString("key")).thenReturn("orders");
        when(resultSet.getString("name")).thenReturn("Orders");
        when(resultSet.getString("type")).thenReturn("COUNTABLE");
        when(resultSet.getString("direction")).thenReturn("MORE_IS_BETTER");
        when(resultSet.getString("severity")).thenReturn("HIGH");
        when(resultSet.getObject("deviation_threshold", BigDecimal.class)).thenReturn(new BigDecimal("0.10"));

        MetricDefinition response = metricDefinitionRowMapper.mapRow(resultSet, 0);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.key()).isEqualTo("orders");
        assertThat(response.type()).isEqualTo(MetricType.COUNTABLE);
    }
}

@ExtendWith(MockitoExtension.class)
class MetricEventRepositoryTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<Boolean> booleanQuerySpec;

    private MetricEventRepository metricEventRepository;

    @BeforeEach
    void setUp() {
        metricEventRepository = new MetricEventRepository(jdbcClient);
    }

    @Test
    void shouldSaveMetricEventsAndCheckUniqueExistence() {
        UUID userId = UUID.randomUUID();
        MetricEvent metricEvent =
                new MetricEvent(UUID.randomUUID(), userId, "orders", Instant.parse("2026-04-05T10:00:00Z"));
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);
        when(statementSpec.query(Boolean.class)).thenReturn(booleanQuerySpec);
        when(booleanQuerySpec.single()).thenReturn(Boolean.TRUE);

        metricEventRepository.save(metricEvent);

        assertThat(metricEventRepository.existsUniqueEventForUser(userId, "orders"))
                .isTrue();
        verify(statementSpec).param("timestamp", metricEvent.timestamp());
        verify(statementSpec, times(2)).param("metricKey", "orders");
    }
}

@ExtendWith(MockitoExtension.class)
class ExperimentMetricBindingRepositoryTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<String> stringQuerySpec;

    @Captor
    private ArgumentCaptor<SqlParameterSource[]> sqlParameterSourcesCaptor;

    private ExperimentMetricBindingRepository experimentMetricBindingRepository;

    @BeforeEach
    void setUp() {
        experimentMetricBindingRepository =
                new ExperimentMetricBindingRepository(jdbcClient, namedParameterJdbcTemplate);
    }

    @Test
    void shouldFindMetricKeysAndUpdateBindings() {
        UUID experimentId = UUID.randomUUID();
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.query(String.class)).thenReturn(stringQuerySpec);
        when(stringQuerySpec.list()).thenReturn(List.of("orders", "revenue"));
        when(statementSpec.update()).thenReturn(1);
        when(namedParameterJdbcTemplate.batchUpdate(anyString(), any(SqlParameterSource[].class)))
                .thenReturn(new int[] {1, 1});

        assertThat(experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId))
                .containsExactly("orders", "revenue");

        experimentMetricBindingRepository.updateMetricKeys(experimentId, List.of("orders", "revenue"));

        verify(namedParameterJdbcTemplate)
                .batchUpdate(contains("INSERT INTO experiment_metrics"), sqlParameterSourcesCaptor.capture());
        assertThat(sqlParameterSourcesCaptor.getValue()).hasSize(2);
    }

    @Test
    void shouldSkipConflictQueryAndBatchInsertWhenMetricKeysAreEmpty() {
        UUID experimentId = UUID.randomUUID();
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        assertThat(experimentMetricBindingRepository.findConflictingMetricKeys(experimentId, List.of()))
                .isEmpty();
        experimentMetricBindingRepository.updateMetricKeys(experimentId, List.of());
    }

    @Test
    void shouldFindConflictingMetricKeysWhenMetricKeysPresent() {
        UUID experimentId = UUID.randomUUID();
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.query(String.class)).thenReturn(stringQuerySpec);
        when(stringQuerySpec.list()).thenReturn(List.of("orders"));

        assertThat(experimentMetricBindingRepository.findConflictingMetricKeys(experimentId, List.of("orders")))
                .containsExactly("orders");
        verify(statementSpec).param("runningState", ExperimentState.RUNNING.toString());
    }
}

@ExtendWith(MockitoExtension.class)
class ReportRepositoriesTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<AssignmentVariantAggregate> assignmentQuerySpec;

    @Mock
    private JdbcClient.MappedQuerySpec<UniqueMetricVariantAggregate> uniqueQuerySpec;

    @Mock
    private JdbcClient.MappedQuerySpec<CountableMetricVariantAggregate> countableQuerySpec;

    @SuppressWarnings("unchecked")
    @Test
    void shouldMapReportRepositoryAggregates() throws Exception {
        UUID variantId = UUID.randomUUID();
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("variant_id", UUID.class)).thenReturn(variantId);
        when(resultSet.getInt("participants")).thenReturn(7);
        when(resultSet.getInt("participants_with_metric_event")).thenReturn(5);
        when(resultSet.getInt("total_metric_events")).thenReturn(9);

        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);

        AtomicReference<RowMapper<AssignmentVariantAggregate>> assignmentMapper = new AtomicReference<>();
        when(statementSpec.query(any(RowMapper.class))).thenAnswer(invocation -> {
            Object rowMapper = invocation.getArgument(0);
            if (assignmentMapper.get() == null) {
                assignmentMapper.set((RowMapper<AssignmentVariantAggregate>) rowMapper);
                return assignmentQuerySpec;
            }
            return uniqueQuerySpec;
        });
        AtomicReference<RowMapper<UniqueMetricVariantAggregate>> uniqueMapper = new AtomicReference<>();
        when(uniqueQuerySpec.list()).thenAnswer(invocation -> {
            UniqueMetricVariantAggregate aggregate = uniqueMapper.get().mapRow(resultSet, 0);
            return List.of(aggregate);
        });
        when(assignmentQuerySpec.list()).thenAnswer(invocation -> {
            AssignmentVariantAggregate aggregate = assignmentMapper.get().mapRow(resultSet, 0);
            return List.of(aggregate);
        });

        AssignmentEventReportRepository assignmentRepository = new AssignmentEventReportRepository(jdbcClient);
        ExperimentReportWindow reportWindow = reportWindow();
        assertThat(assignmentRepository.findParticipantCountsByVariant(UUID.randomUUID(), reportWindow))
                .containsExactly(new AssignmentVariantAggregate(variantId, 7));

        when(statementSpec.query(any(RowMapper.class))).thenAnswer(invocation -> {
            uniqueMapper.set(invocation.getArgument(0));
            return uniqueQuerySpec;
        });
        UniqueMetricEventReportRepository uniqueRepository = new UniqueMetricEventReportRepository(jdbcClient);
        assertThat(uniqueRepository.findParticipantCountsByVariant(UUID.randomUUID(), "orders", reportWindow))
                .containsExactly(new UniqueMetricVariantAggregate(variantId, 5));

        AtomicReference<RowMapper<CountableMetricVariantAggregate>> countableMapper = new AtomicReference<>();
        when(statementSpec.query(any(RowMapper.class))).thenAnswer(invocation -> {
            countableMapper.set(invocation.getArgument(0));
            return countableQuerySpec;
        });
        when(countableQuerySpec.list()).thenAnswer(invocation -> {
            CountableMetricVariantAggregate aggregate = countableMapper.get().mapRow(resultSet, 0);
            return List.of(aggregate);
        });
        CountableMetricEventReportRepository countableRepository = new CountableMetricEventReportRepository(jdbcClient);
        assertThat(countableRepository.findMetricStatsByVariant(UUID.randomUUID(), "orders", reportWindow))
                .containsExactly(new CountableMetricVariantAggregate(variantId, 5, 9));
    }

    private ExperimentReportWindow reportWindow() {
        return new ExperimentReportWindow(Instant.parse("2026-04-05T09:00:00Z"), Instant.parse("2026-04-05T10:00:00Z"));
    }
}

@ExtendWith(MockitoExtension.class)
class ExperimentConflictRepositoryTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private ExperimentRowMapper experimentRowMapper;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<Experiment> mappedQuerySpec;

    private ExperimentConflictRepository experimentConflictRepository;

    @BeforeEach
    void setUp() {
        experimentConflictRepository = new ExperimentConflictRepository(jdbcClient, experimentRowMapper);
    }

    @Test
    void shouldFindPotentialConflicts() {
        UUID experimentId = UUID.randomUUID();
        Experiment experiment = new Experiment(
                UUID.randomUUID(), "flag-orders", "CHECKOUT", List.of(), ExperimentState.RUNNING, 3L, null, null);
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.query(experimentRowMapper)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.list()).thenReturn(List.of(experiment));

        assertThat(experimentConflictRepository.findAll(experimentId, "flag-orders", "CHECKOUT"))
                .containsExactly(experiment);
        verify(statementSpec).param("runningState", ExperimentState.RUNNING.toString());
    }
}

@ExtendWith(MockitoExtension.class)
class ExperimentMetricRiskRepositoryTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<ExperimentMetricRisk> mappedQuerySpec;

    private ExperimentMetricRiskRepository experimentMetricRiskRepository;

    @BeforeEach
    void setUp() {
        experimentMetricRiskRepository = new ExperimentMetricRiskRepository(jdbcClient);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldFindSaveUpdateAndMapExperimentMetricRisks() throws Exception {
        UUID riskId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant openedAt = Instant.parse("2026-04-05T10:00:00Z");
        Instant resolvedAt = Instant.parse("2026-04-05T11:00:00Z");
        Instant autoPausedAt = Instant.parse("2026-04-05T12:00:00Z");
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("id", UUID.class)).thenReturn(riskId);
        when(resultSet.getObject("experiment_id", UUID.class)).thenReturn(experimentId);
        when(resultSet.getString("metric_key")).thenReturn("orders");
        when(resultSet.getObject("variant_id", UUID.class)).thenReturn(variantId);
        when(resultSet.getString("status")).thenReturn("OPEN");
        when(resultSet.getTimestamp("opened_at")).thenReturn(Timestamp.from(openedAt));
        when(resultSet.getTimestamp("resolved_at")).thenReturn(Timestamp.from(resolvedAt), null);
        when(resultSet.getString("resolution_comment")).thenReturn("manual");
        when(resultSet.getTimestamp("last_evaluated_at")).thenReturn(Timestamp.from(openedAt.plusSeconds(60)));
        when(resultSet.getObject("last_bad_deviation", BigDecimal.class)).thenReturn(new BigDecimal("0.12"));
        when(resultSet.getObject("worst_bad_deviation", BigDecimal.class)).thenReturn(new BigDecimal("0.15"));
        when(resultSet.getTimestamp("auto_paused_at")).thenReturn(Timestamp.from(autoPausedAt), null);

        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        AtomicReference<RowMapper<ExperimentMetricRisk>> mapperRef = new AtomicReference<>();
        when(statementSpec.query(any(RowMapper.class))).thenAnswer(invocation -> {
            mapperRef.set(invocation.getArgument(0));
            return mappedQuerySpec;
        });
        when(mappedQuerySpec.list())
                .thenAnswer(invocation -> List.of(mapperRef.get().mapRow(resultSet, 0)));
        when(mappedQuerySpec.optional())
                .thenAnswer(invocation -> Optional.of(mapperRef.get().mapRow(resultSet, 0)));
        when(statementSpec.update()).thenReturn(1);

        List<ExperimentMetricRisk> risks =
                experimentMetricRiskRepository.findByExperimentAndMetric(experimentId, "orders");
        Optional<ExperimentMetricRisk> risk = experimentMetricRiskRepository.findById(riskId);

        assertThat(risks).hasSize(1);
        assertThat(risk).isPresent();
        assertThat(risks.getFirst().autoPausedAt()).isEqualTo(autoPausedAt);

        ExperimentMetricRisk mappedRisk = risks.getFirst();
        experimentMetricRiskRepository.save(mappedRisk);
        experimentMetricRiskRepository.update(mappedRisk);

        verify(statementSpec, times(2)).param("status", "OPEN");
        verify(statementSpec, times(2)).param("resolutionComment", "manual");

        Method toInstant =
                ExperimentMetricRiskRepository.class.getDeclaredMethod("toInstant", ResultSet.class, String.class);
        toInstant.setAccessible(true);
        assertThat(toInstant.invoke(experimentMetricRiskRepository, resultSet, "resolved_at"))
                .isNull();
    }
}
