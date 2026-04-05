package io.github.rehody.abplatform.repository;

import io.github.rehody.abplatform.model.AssignmentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AssignmentEventRepository {

    private static final String INSERT_ASSIGNMENT_EVENT_SQL = """
        INSERT INTO assignment_events (id, user_id, variant_id, experiment_id, timestamp)
        VALUES (:id, :userId, :variantId, :experimentId, :timestamp)
        ON CONFLICT (experiment_id, user_id) DO NOTHING
        """;

    private final JdbcClient jdbcClient;

    public void saveIfAbsent(AssignmentEvent assignmentEvent) {
        jdbcClient
                .sql(INSERT_ASSIGNMENT_EVENT_SQL)
                .param("id", assignmentEvent.id())
                .param("userId", assignmentEvent.userId())
                .param("variantId", assignmentEvent.variantId())
                .param("experimentId", assignmentEvent.experimentId())
                .param("timestamp", assignmentEvent.timestamp())
                .update();
    }
}
