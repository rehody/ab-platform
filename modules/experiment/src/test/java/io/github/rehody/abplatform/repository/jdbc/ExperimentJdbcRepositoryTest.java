package io.github.rehody.abplatform.repository.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.repository.rowmapper.ExperimentRowMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class ExperimentJdbcRepositoryTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private ExperimentRowMapper experimentRowMapper;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<Experiment> mappedQuerySpec;

    @Mock
    private JdbcClient.MappedQuerySpec<Boolean> booleanQuerySpec;

    @Mock
    private JdbcClient.MappedQuerySpec<Long> longQuerySpec;

    @Mock
    private JdbcClient.MappedQuerySpec<String> stringQuerySpec;

    private ExperimentJdbcRepository experimentJdbcRepository;

    @BeforeEach
    void setUp() {
        experimentJdbcRepository = new ExperimentJdbcRepository(jdbcClient, experimentRowMapper);
    }

    @Test
    void insert_shouldWriteAllParametersAndExecuteInsertUpdate() {
        Experiment experiment = new Experiment(
                UUID.randomUUID(), "flag-a", "CHECKOUT", List.of(), ExperimentState.DRAFT, 0L, null, null);
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        experimentJdbcRepository.insert(experiment);

        verify(jdbcClient).sql(contains("INSERT INTO experiments"));
        verify(statementSpec).param("id", experiment.id());
        verify(statementSpec).param("flagKey", "flag-a");
        verify(statementSpec).param("domain", "CHECKOUT");
        verify(statementSpec).param("state", "DRAFT");
        verify(statementSpec).param("version", 0L);
        verify(statementSpec).param("startedAt", null);
        verify(statementSpec).param("completedAt", null);
        verify(statementSpec).update();
    }

    @Test
    void findById_shouldReturnMappedExperimentWhenRepositoryContainsRecord() {
        Experiment experiment = new Experiment(
                UUID.randomUUID(),
                "flag-b",
                "CHECKOUT",
                List.of(),
                ExperimentState.RUNNING,
                3L,
                Instant.parse("2026-03-30T10:15:30Z"),
                null);
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("id", experiment.id())).thenReturn(statementSpec);
        when(statementSpec.query(experimentRowMapper)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.of(experiment));

        Optional<Experiment> response = experimentJdbcRepository.findById(experiment.id());

        assertThat(response).contains(experiment);
        verify(jdbcClient).sql(contains("SELECT id, flag_key, domain_key, state, version, started_at, completed_at"));
    }

    @Test
    void findByFlagKey_shouldReturnMappedExperimentWhenRepositoryContainsRecord() {
        Experiment experiment = new Experiment(
                UUID.randomUUID(), "flag-c", "CHECKOUT", List.of(), ExperimentState.APPROVED, 4L, null, null);
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("flagKey", "flag-c")).thenReturn(statementSpec);
        when(statementSpec.query(experimentRowMapper)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.of(experiment));

        Optional<Experiment> response = experimentJdbcRepository.findByFlagKey("flag-c");

        assertThat(response).contains(experiment);
    }

    @Test
    void findAll_shouldReturnMappedExperiments() {
        List<Experiment> experiments = List.of(new Experiment(
                UUID.randomUUID(), "flag-d", "CHECKOUT", List.of(), ExperimentState.PAUSED, 5L, null, null));
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.query(experimentRowMapper)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.list()).thenReturn(experiments);

        assertThat(experimentJdbcRepository.findAll()).isEqualTo(experiments);
    }

    @Test
    void existsById_shouldReturnBooleanFromJdbc() {
        UUID id = UUID.randomUUID();
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("id", id)).thenReturn(statementSpec);
        when(statementSpec.query(Boolean.class)).thenReturn(booleanQuerySpec);
        when(booleanQuerySpec.single()).thenReturn(Boolean.TRUE);

        assertThat(experimentJdbcRepository.existsById(id)).isTrue();
    }

    @Test
    void existsByFlagKey_shouldReturnBooleanFromJdbc() {
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("flagKey", "flag-e")).thenReturn(statementSpec);
        when(statementSpec.query(Boolean.class)).thenReturn(booleanQuerySpec);
        when(booleanQuerySpec.single()).thenReturn(Boolean.FALSE);

        assertThat(experimentJdbcRepository.existsByFlagKey("flag-e")).isFalse();
    }

    @Test
    void update_shouldWriteExpectedVersionAndReturnUpdatedVersion() {
        Experiment experiment = new Experiment(
                UUID.randomUUID(),
                "flag-f",
                "CHECKOUT",
                List.of(),
                ExperimentState.ARCHIVED,
                7L,
                Instant.parse("2026-03-30T10:15:30Z"),
                Instant.parse("2026-03-30T11:15:30Z"));
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.query(Long.class)).thenReturn(longQuerySpec);
        when(longQuerySpec.optional()).thenReturn(Optional.of(8L));

        Optional<Long> updatedVersion = experimentJdbcRepository.update(experiment);

        verify(jdbcClient).sql(contains("UPDATE experiments"));
        verify(statementSpec).param("id", experiment.id());
        verify(statementSpec).param("flagKey", "flag-f");
        verify(statementSpec).param("domain", "CHECKOUT");
        verify(statementSpec).param("state", "ARCHIVED");
        verify(statementSpec).param("startedAt", experiment.startedAt());
        verify(statementSpec).param("completedAt", experiment.completedAt());
        verify(statementSpec).param("expectedVersion", 7L);
        assertThat(updatedVersion).contains(8L);
    }

    @Test
    void deleteById_shouldExecuteDeleteStatement() {
        UUID id = UUID.randomUUID();
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("id", id)).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        int deletedRows = experimentJdbcRepository.deleteById(id);

        assertThat(deletedRows).isEqualTo(1);
        verify(jdbcClient).sql(contains("DELETE FROM experiments"));
    }

    @Test
    void findVersionById_shouldReturnVersionWhenPresent() {
        UUID id = UUID.randomUUID();
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("id", id)).thenReturn(statementSpec);
        when(statementSpec.query(Long.class)).thenReturn(longQuerySpec);
        when(longQuerySpec.optional()).thenReturn(Optional.of(9L));

        assertThat(experimentJdbcRepository.findVersionById(id)).contains(9L);
    }

    @Test
    void findFlagKeyById_shouldReturnFlagKeyWhenPresent() {
        UUID id = UUID.randomUUID();
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("id", id)).thenReturn(statementSpec);
        when(statementSpec.query(String.class)).thenReturn(stringQuerySpec);
        when(stringQuerySpec.optional()).thenReturn(Optional.of("flag-g"));

        assertThat(experimentJdbcRepository.findFlagKeyById(id)).contains("flag-g");
    }

    @Test
    void incrementVersion_shouldWriteExpectedVersionAndExecuteUpdateStatement() {
        UUID id = UUID.randomUUID();
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        int updatedRows = experimentJdbcRepository.incrementVersion(id, 10L);

        verify(jdbcClient).sql(contains("SET version = version + 1"));
        verify(statementSpec).param("id", id);
        verify(statementSpec).param("expectedVersion", 10L);
        assertThat(updatedRows).isEqualTo(1);
    }
}
