package io.github.rehody.abplatform.repository.jdbc;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.repository.rowmapper.ExperimentRowMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExperimentJdbcRepository {

    private static final String INSERT_EXPERIMENT_SQL = """
        INSERT INTO experiments (id, flag_key, domain_key, state, version, started_at, completed_at)
        VALUES (:id, :flagKey, :domain, :state, :version, :startedAt, :completedAt)
        """;

    private static final String UPDATE_EXPERIMENT_SQL = """
        UPDATE experiments
        SET flag_key = :flagKey,
            domain_key = :domain,
            state = :state,
            started_at = :startedAt,
            completed_at = :completedAt,
            version = version + 1
        WHERE id = :id
          AND version = :expectedVersion
        RETURNING version
        """;

    private static final String SELECT_EXPERIMENT_VERSION_BY_ID_SQL = """
        SELECT version
        FROM experiments
        WHERE id = :id
        """;

    private static final String SELECT_EXPERIMENT_FLAG_KEY_BY_ID_SQL = """
        SELECT flag_key
        FROM experiments
        WHERE id = :id
        """;

    private static final String INCREMENT_EXPERIMENT_VERSION_SQL = """
        UPDATE experiments
        SET version = version + 1
        WHERE id = :id
          AND version = :expectedVersion
        """;

    private static final String SELECT_EXPERIMENT_BY_ID_SQL = """
        SELECT id, flag_key, domain_key, state, version, started_at, completed_at
        FROM experiments
        WHERE id = :id
        """;

    private static final String SELECT_EXPERIMENT_BY_FLAG_KEY_SQL = """
        SELECT id, flag_key, domain_key, state, version, started_at, completed_at
        FROM experiments
        WHERE flag_key = :flagKey
        """;

    private static final String SELECT_RUNNING_EXPERIMENT_BY_FLAG_KEY_SQL = """
        SELECT id, flag_key, domain_key, state, version, started_at, completed_at
        FROM experiments
        WHERE flag_key = :flagKey
          AND state = :runningState
        ORDER BY started_at DESC NULLS LAST, created_at DESC, id
        LIMIT 1
        """;

    private static final String SELECT_ALL_EXPERIMENTS_SQL = """
        SELECT id, flag_key, domain_key, state, version, started_at, completed_at
        FROM experiments
        ORDER BY created_at DESC, id
        """;

    private static final String SELECT_EXPERIMENTS_BY_STATE_SQL = """
        SELECT id, flag_key, domain_key, state, version, started_at, completed_at
        FROM experiments
        WHERE state = :state
        ORDER BY created_at DESC, id
        """;

    private static final String EXISTS_EXPERIMENT_BY_ID_SQL = """
        SELECT EXISTS(
            SELECT 1
            FROM experiments
            WHERE id = :id
        )
        """;

    private static final String EXISTS_EXPERIMENT_BY_FLAG_KEY_SQL = """
        SELECT EXISTS(
            SELECT 1
            FROM experiments
            WHERE flag_key = :flagKey
        )
        """;

    private static final String DELETE_EXPERIMENT_BY_ID_SQL = """
        DELETE FROM experiments
        WHERE id = :id
        """;

    private final JdbcClient jdbcClient;
    private final ExperimentRowMapper experimentRowMapper;

    public void insert(Experiment experiment) {
        jdbcClient
                .sql(INSERT_EXPERIMENT_SQL)
                .param("id", experiment.id())
                .param("flagKey", experiment.flagKey())
                .param("domain", experiment.domain())
                .param("state", experiment.state().name())
                .param("version", experiment.version())
                .param("startedAt", experiment.startedAt())
                .param("completedAt", experiment.completedAt())
                .update();
    }

    public Optional<Experiment> findById(UUID id) {
        return jdbcClient
                .sql(SELECT_EXPERIMENT_BY_ID_SQL)
                .param("id", id)
                .query(experimentRowMapper)
                .optional();
    }

    public Optional<Experiment> findByFlagKey(String flagKey) {
        return jdbcClient
                .sql(SELECT_EXPERIMENT_BY_FLAG_KEY_SQL)
                .param("flagKey", flagKey)
                .query(experimentRowMapper)
                .optional();
    }

    public Optional<Experiment> findRunningByFlagKey(String flagKey) {
        return jdbcClient
                .sql(SELECT_RUNNING_EXPERIMENT_BY_FLAG_KEY_SQL)
                .param("flagKey", flagKey)
                .param("runningState", ExperimentState.RUNNING.name())
                .query(experimentRowMapper)
                .optional();
    }

    public List<Experiment> findAll() {
        return jdbcClient
                .sql(SELECT_ALL_EXPERIMENTS_SQL)
                .query(experimentRowMapper)
                .list();
    }

    public List<Experiment> findByState(ExperimentState state) {
        return jdbcClient
                .sql(SELECT_EXPERIMENTS_BY_STATE_SQL)
                .param("state", state.name())
                .query(experimentRowMapper)
                .list();
    }

    public boolean existsById(UUID id) {
        return jdbcClient
                .sql(EXISTS_EXPERIMENT_BY_ID_SQL)
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    public boolean existsByFlagKey(String flagKey) {
        return jdbcClient
                .sql(EXISTS_EXPERIMENT_BY_FLAG_KEY_SQL)
                .param("flagKey", flagKey)
                .query(Boolean.class)
                .single();
    }

    public Optional<Long> update(Experiment experiment) {
        return jdbcClient
                .sql(UPDATE_EXPERIMENT_SQL)
                .param("id", experiment.id())
                .param("flagKey", experiment.flagKey())
                .param("domain", experiment.domain())
                .param("state", experiment.state().name())
                .param("startedAt", experiment.startedAt())
                .param("completedAt", experiment.completedAt())
                .param("expectedVersion", experiment.version())
                .query(Long.class)
                .optional();
    }

    public int deleteById(UUID id) {
        return jdbcClient.sql(DELETE_EXPERIMENT_BY_ID_SQL).param("id", id).update();
    }

    public Optional<Long> findVersionById(UUID id) {
        return jdbcClient
                .sql(SELECT_EXPERIMENT_VERSION_BY_ID_SQL)
                .param("id", id)
                .query(Long.class)
                .optional();
    }

    public Optional<String> findFlagKeyById(UUID id) {
        return jdbcClient
                .sql(SELECT_EXPERIMENT_FLAG_KEY_BY_ID_SQL)
                .param("id", id)
                .query(String.class)
                .optional();
    }

    public int incrementVersion(UUID id, long expectedVersion) {
        return jdbcClient
                .sql(INCREMENT_EXPERIMENT_VERSION_SQL)
                .param("id", id)
                .param("expectedVersion", expectedVersion)
                .update();
    }
}
