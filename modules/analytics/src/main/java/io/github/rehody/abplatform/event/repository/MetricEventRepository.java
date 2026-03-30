package io.github.rehody.abplatform.event.repository;

import io.github.rehody.abplatform.event.model.MetricEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MetricEventRepository {

    private static final String INSERT_METRIC_EVENT_SQL = """
        INSERT INTO metric_events (id, user_id, metric_key, timestamp)
        VALUES (:id, :userId, :metricKey, :timestamp)
        """;

    private static final String EXISTS_UNIQUE_METRIC_EVENT_FOR_USER_SQL = """
        SELECT EXISTS(
            SELECT 1
            FROM metric_events
            WHERE user_id = :userId
              AND metric_key = :metricKey
        )
        """;

    private final JdbcClient jdbcClient;

    public void save(MetricEvent metricEvent) {
        jdbcClient
                .sql(INSERT_METRIC_EVENT_SQL)
                .param("id", metricEvent.id())
                .param("userId", metricEvent.userId())
                .param("metricKey", metricEvent.metricKey())
                .param("timestamp", metricEvent.timestamp())
                .update();
    }

    public boolean existsUniqueEventForUser(UUID userId, String metricKey) {
        return jdbcClient
                .sql(EXISTS_UNIQUE_METRIC_EVENT_FOR_USER_SQL)
                .param("userId", userId)
                .param("metricKey", metricKey)
                .query(Boolean.class)
                .single();
    }
}
