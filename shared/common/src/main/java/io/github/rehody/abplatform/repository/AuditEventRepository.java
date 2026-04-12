package io.github.rehody.abplatform.repository;

import io.github.rehody.abplatform.model.audit.AuditEvent;
import io.github.rehody.abplatform.model.audit.AuditTargetType;
import io.github.rehody.abplatform.repository.mapper.AuditDetailsJsonSerializer;
import io.github.rehody.abplatform.repository.rowmapper.AuditEventRowMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuditEventRepository {

    private static final String INSERT_AUDIT_EVENT_SQL = """
        INSERT INTO audit_events (
            action,
            actor_type,
            user_actor_id,
            system_actor_name,
            target_type,
            target_id,
            event_timestamp,
            details
        )
        VALUES (
            :action,
            :actorType,
            :userActorId,
            :systemActorName,
            :targetType,
            :targetId,
            :eventTimestamp,
            :details::jsonb
        )
        """;

    private static final String FIND_BY_TARGET_SQL = """
        SELECT
            action,
            actor_type,
            user_actor_id,
            system_actor_name,
            target_type,
            target_id,
            event_timestamp,
            details
        FROM audit_events
        WHERE target_type = :targetType
          AND target_id = :targetId
        ORDER BY event_timestamp DESC, id DESC
        """;

    private final JdbcClient jdbcClient;
    private final AuditDetailsJsonSerializer auditDetailsJsonSerializer;
    private final AuditEventRowMapper auditEventRowMapper;

    public void save(AuditEvent auditEvent) {
        jdbcClient
                .sql(INSERT_AUDIT_EVENT_SQL)
                .param("action", auditEvent.action().toString())
                .param("actorType", auditEvent.actor().type().toString())
                .param("userActorId", auditEvent.actor().userId())
                .param("systemActorName", auditEvent.actor().systemName())
                .param("targetType", auditEvent.target().type().toString())
                .param("targetId", auditEvent.target().id())
                .param("eventTimestamp", auditEvent.timestamp())
                .param("details", auditDetailsJsonSerializer.serialize(auditEvent.details()))
                .update();
    }

    public java.util.List<AuditEvent> findByTarget(AuditTargetType targetType, UUID targetId) {
        return jdbcClient
                .sql(FIND_BY_TARGET_SQL)
                .param("targetType", targetType.toString())
                .param("targetId", targetId)
                .query(auditEventRowMapper)
                .list();
    }
}
