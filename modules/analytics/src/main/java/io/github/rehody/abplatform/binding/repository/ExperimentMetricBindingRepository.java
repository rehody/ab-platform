package io.github.rehody.abplatform.binding.repository;

import io.github.rehody.abplatform.enums.ExperimentState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExperimentMetricBindingRepository {

    private static final String SELECT_METRIC_KEYS_BY_EXPERIMENT_ID_SQL = """
        SELECT metric_key
        FROM experiment_metrics
        WHERE experiment_id = :experimentId
        ORDER BY metric_key
        """;

    private static final String DELETE_BY_EXPERIMENT_ID_SQL = """
        DELETE FROM experiment_metrics
        WHERE experiment_id = :experimentId
        """;

    private static final String INSERT_BINDING_SQL = """
        INSERT INTO experiment_metrics (experiment_id, metric_key)
        VALUES (:experimentId, :metricKey)
        """;

    private static final String SELECT_EXISTS_CONFLICTING_METRIC_KEYS_SQL = """
        SELECT EXISTS (
          SELECT 1
          FROM experiment_metrics em
          WHERE em.metric_key IN (:metricKeys)
            AND em.experiment_id <> :experimentId
            AND EXISTS (
                SELECT 1
                FROM experiments e
                WHERE e.id = em.experiment_id
                  AND e.state = :runningState
              )
        )
        """;

    private final JdbcClient jdbcClient;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<String> findMetricKeysByExperimentId(UUID experimentId) {
        return jdbcClient
                .sql(SELECT_METRIC_KEYS_BY_EXPERIMENT_ID_SQL)
                .param("experimentId", experimentId)
                .query(String.class)
                .list();
    }

    public void updateMetricKeys(UUID experimentId, List<String> metricKeys) {
        deleteByExperimentId(experimentId);
        batchInsert(experimentId, metricKeys);
    }

    public boolean existsConflictingMetricKey(UUID experimentId, List<String> metricKeys) {
        if (metricKeys.isEmpty()) {
            return false;
        }

        return jdbcClient
                .sql(SELECT_EXISTS_CONFLICTING_METRIC_KEYS_SQL)
                .param("experimentId", experimentId)
                .param("metricKeys", metricKeys)
                .param("runningState", ExperimentState.RUNNING.toString())
                .query(Boolean.class)
                .single();
    }

    private void deleteByExperimentId(UUID experimentId) {
        jdbcClient
                .sql(DELETE_BY_EXPERIMENT_ID_SQL)
                .param("experimentId", experimentId)
                .update();
    }

    private void batchInsert(UUID experimentId, List<String> metricKeys) {
        if (metricKeys.isEmpty()) {
            return;
        }

        List<SqlParameterSource> batchParams = new ArrayList<>(metricKeys.size());
        for (String metricKey : metricKeys) {
            batchParams.add(new MapSqlParameterSource()
                    .addValue("experimentId", experimentId)
                    .addValue("metricKey", metricKey));
        }

        namedParameterJdbcTemplate.batchUpdate(INSERT_BINDING_SQL, batchParams.toArray(SqlParameterSource[]::new));
    }
}
