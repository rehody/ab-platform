package io.github.rehody.abplatform.repository.jdbc;

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
        INSERT INTO experiments (id, flag_key, state, version)
        VALUES (:id, :flagKey, :state, :version)
        """;

    private static final String UPDATE_EXPERIMENT_SQL = """
        UPDATE experiments
        SET flag_key = :flagKey,
            state = :state,
            version = version + 1
        WHERE id = :id
          AND version = :expectedVersion
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
        SELECT id, flag_key, state, version
        FROM experiments
        WHERE id = :id
        """;

    private static final String SELECT_EXPERIMENT_BY_FLAG_KEY_SQL = """
        SELECT id, flag_key, state, version
        FROM experiments
        WHERE flag_key = :flagKey
        """;

    private static final String SELECT_ALL_EXPERIMENTS_SQL = """
        SELECT id, flag_key, state, version
        FROM experiments
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
                .param("state", experiment.state().name())
                .param("version", experiment.version())
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

    public List<Experiment> findAll() {
        return jdbcClient
                .sql(SELECT_ALL_EXPERIMENTS_SQL)
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

    public int update(Experiment experiment) {
        return jdbcClient
                .sql(UPDATE_EXPERIMENT_SQL)
                .param("id", experiment.id())
                .param("flagKey", experiment.flagKey())
                .param("state", experiment.state().name())
                .param("expectedVersion", experiment.version())
                .update();
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
