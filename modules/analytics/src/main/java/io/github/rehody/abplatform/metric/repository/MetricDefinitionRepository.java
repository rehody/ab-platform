package io.github.rehody.abplatform.metric.repository;

import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.repository.rowmapper.MetricDefinitionRowMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MetricDefinitionRepository {

    private static final String SELECT_METRIC_DEFINITION_BY_KEY_SQL = """
        SELECT id, key, name, type
        FROM metric_definitions
        WHERE key = :key
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
}
