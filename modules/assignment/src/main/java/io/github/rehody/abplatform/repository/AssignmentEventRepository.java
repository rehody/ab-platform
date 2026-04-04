package io.github.rehody.abplatform.repository;

import java.time.Instant;
import java.util.UUID;
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

    public void saveIfAbsent(UUID experimentId, UUID variantId, UUID userId, Instant timestamp) {
        jdbcClient
                .sql(INSERT_ASSIGNMENT_EVENT_SQL)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("variantId", variantId)
                .param("experimentId", experimentId)
                .param("timestamp", timestamp)
                .update();
    }
}
