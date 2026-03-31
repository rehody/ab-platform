package io.github.rehody.abplatform.report.repository;

import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.repository.aggregate.CountableMetricVariantAggregate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CountableMetricEventReportRepository {

    private static final String SELECT_COUNTABLE_METRIC_STATS_BY_VARIANT_SQL = """
        WITH first_assignments AS (
            SELECT DISTINCT ON (user_id)
                variant_id,
                user_id,
                timestamp
            FROM assignment_events
            WHERE experiment_id = :experimentId
              AND timestamp >= :trackedFrom
              AND timestamp < :trackedTo
            ORDER BY user_id, timestamp, id
        )
        SELECT
            fa.variant_id,
            COUNT(DISTINCT me.user_id) AS participants_with_metric_event,
            COUNT(*) AS total_metric_events
        FROM first_assignments fa
        JOIN metric_events me
          ON me.user_id = fa.user_id
         AND me.metric_key = :metricKey
         AND me.timestamp >= fa.timestamp
         AND me.timestamp < :trackedTo
        GROUP BY fa.variant_id
        """;

    private final JdbcClient jdbcClient;

    public List<CountableMetricVariantAggregate> findMetricStatsByVariant(
            UUID experimentId, String metricKey, ExperimentReportWindow reportWindow) {
        return jdbcClient
                .sql(SELECT_COUNTABLE_METRIC_STATS_BY_VARIANT_SQL)
                .param("experimentId", experimentId)
                .param("metricKey", metricKey)
                .param("trackedFrom", reportWindow.trackedFrom())
                .param("trackedTo", reportWindow.trackedTo())
                .query((rs, _) -> new CountableMetricVariantAggregate(
                        rs.getObject("variant_id", UUID.class),
                        rs.getInt("participants_with_metric_event"),
                        rs.getInt("total_metric_events")))
                .list();
    }
}
