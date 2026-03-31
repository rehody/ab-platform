package io.github.rehody.abplatform.report.repository;

import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.repository.aggregate.UniqueMetricVariantAggregate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UniqueMetricEventReportRepository {

    private static final String SELECT_PARTICIPANTS_WITH_METRIC_EVENT_BY_VARIANT_SQL = """
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
        SELECT fa.variant_id, COUNT(*) AS participants_with_metric_event
        FROM first_assignments fa
        WHERE EXISTS (
            SELECT 1
            FROM metric_events me
            WHERE me.user_id = fa.user_id
              AND me.metric_key = :metricKey
              AND me.timestamp >= fa.timestamp
              AND me.timestamp < :trackedTo
        )
        GROUP BY fa.variant_id
        """;

    private final JdbcClient jdbcClient;

    public List<UniqueMetricVariantAggregate> findParticipantCountsByVariant(
            UUID experimentId, String metricKey, ExperimentReportWindow reportWindow) {
        return jdbcClient
                .sql(SELECT_PARTICIPANTS_WITH_METRIC_EVENT_BY_VARIANT_SQL)
                .param("experimentId", experimentId)
                .param("metricKey", metricKey)
                .param("trackedFrom", reportWindow.trackedFrom())
                .param("trackedTo", reportWindow.trackedTo())
                .query((rs, _) -> new UniqueMetricVariantAggregate(
                        rs.getObject("variant_id", UUID.class), rs.getInt("participants_with_metric_event")))
                .list();
    }
}
