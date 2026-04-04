package io.github.rehody.abplatform.metric.repository;

import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.repository.rowmapper.MetricDefinitionRowMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MetricDefinitionRepository {

    private static final String SELECT_METRIC_DEFINITION_BY_KEY_SQL = """
        SELECT id, key, name, type, direction, severity, deviation_threshold
        FROM metric_definitions
        WHERE key = :key
        """;

    private static final String SELECT_ALL_METRIC_DEFINITIONS_SQL = """
        SELECT id, key, name, type, direction, severity, deviation_threshold
        FROM metric_definitions
        ORDER BY key
        """;

    private static final String INSERT_METRIC_DEFINITION_SQL = """
        INSERT INTO metric_definitions (id, key, name, type, direction, severity, deviation_threshold)
        VALUES (:id, :key, :name, :type, :direction, :severity, :deviationThreshold)
        """;

    private static final String UPDATE_METRIC_DEFINITION_SQL = """
        UPDATE metric_definitions
        SET name = :name,
            type = :type,
            direction = :direction,
            severity = :severity,
            deviation_threshold = :deviationThreshold
        WHERE key = :key
        """;

    private static final String EXISTS_METRIC_DEFINITION_BY_KEY_SQL = """
        SELECT EXISTS(
            SELECT 1
            FROM metric_definitions
            WHERE key = :key
        )
        """;

    private final JdbcClient jdbcClient;
    private final MetricDefinitionRowMapper metricDefinitionRowMapper;

    public Optional<MetricDefinition> findByKey(String key) {
        return jdbcClient
                .sql(SELECT_METRIC_DEFINITION_BY_KEY_SQL)
                .param("key", key)
                .query(metricDefinitionRowMapper)
                .optional();
    }

    public List<MetricDefinition> findAll() {
        return jdbcClient
                .sql(SELECT_ALL_METRIC_DEFINITIONS_SQL)
                .query(metricDefinitionRowMapper)
                .list();
    }

    public void save(MetricDefinition metricDefinition) {
        jdbcClient
                .sql(INSERT_METRIC_DEFINITION_SQL)
                .param("id", metricDefinition.id())
                .param("key", metricDefinition.key())
                .param("name", metricDefinition.name())
                .param("type", metricDefinition.type().name())
                .param("direction", metricDefinition.direction().name())
                .param("severity", metricDefinition.severity().name())
                .param("deviationThreshold", metricDefinition.deviationThreshold())
                .update();
    }

    public int update(MetricDefinition metricDefinition) {
        return jdbcClient
                .sql(UPDATE_METRIC_DEFINITION_SQL)
                .param("key", metricDefinition.key())
                .param("name", metricDefinition.name())
                .param("type", metricDefinition.type().name())
                .param("direction", metricDefinition.direction().name())
                .param("severity", metricDefinition.severity().name())
                .param("deviationThreshold", metricDefinition.deviationThreshold())
                .update();
    }

    public boolean existsByKey(String key) {
        return jdbcClient
                .sql(EXISTS_METRIC_DEFINITION_BY_KEY_SQL)
                .param("key", key)
                .query(Boolean.class)
                .single();
    }
}
