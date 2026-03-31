package io.github.rehody.abplatform.report.repository;

import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.repository.aggregate.AssignmentVariantAggregate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AssignmentEventReportRepository {

    private static final String SELECT_PARTICIPANT_COUNTS_BY_VARIANT_SQL = """
        WITH first_assignments AS (
            SELECT DISTINCT ON (user_id)
                variant_id,
                user_id
            FROM assignment_events
            WHERE experiment_id = :experimentId
              AND timestamp >= :trackedFrom
              AND timestamp < :trackedTo
            ORDER BY user_id, timestamp, id
        )
        SELECT variant_id, COUNT(*) AS participants
        FROM first_assignments
        GROUP BY variant_id
        """;

    private final JdbcClient jdbcClient;

    public List<AssignmentVariantAggregate> findParticipantCountsByVariant(
            UUID experimentId, ExperimentReportWindow reportWindow) {
        return jdbcClient
                .sql(SELECT_PARTICIPANT_COUNTS_BY_VARIANT_SQL)
                .param("experimentId", experimentId)
                .param("trackedFrom", reportWindow.trackedFrom())
                .param("trackedTo", reportWindow.trackedTo())
                .query((rs, _) -> new AssignmentVariantAggregate(
                        rs.getObject("variant_id", UUID.class), rs.getInt("participants")))
                .list();
    }
}
