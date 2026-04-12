package io.github.rehody.abplatform.repository.rowmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActorType;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditEvent;
import io.github.rehody.abplatform.model.audit.AuditTargetType;
import io.github.rehody.abplatform.repository.mapper.AuditDetailsJsonSerializer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditEventRowMapperTest {

    @Mock
    private ResultSet resultSet;

    @Mock
    private AuditDetailsJsonSerializer auditDetailsJsonSerializer;

    @Test
    void mapRow_shouldMapUserActorEvent() throws SQLException {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-04-12T10:00:00Z");
        AuditEventRowMapper auditEventRowMapper = new AuditEventRowMapper(auditDetailsJsonSerializer);
        when(resultSet.getString("action")).thenReturn(AuditAction.EXPERIMENT_CREATED.name());
        when(resultSet.getString("actor_type")).thenReturn(AuditActorType.USER.name());
        when(resultSet.getObject("user_actor_id", UUID.class)).thenReturn(userId);
        when(resultSet.getString("target_type")).thenReturn(AuditTargetType.EXPERIMENT.name());
        when(resultSet.getObject("target_id", UUID.class)).thenReturn(targetId);
        when(resultSet.getTimestamp("event_timestamp")).thenReturn(Timestamp.from(timestamp));
        when(resultSet.getString("details")).thenReturn("{\"state\":\"DRAFT\"}");
        when(auditDetailsJsonSerializer.deserialize("{\"state\":\"DRAFT\"}"))
                .thenReturn(AuditDetails.entry("state", "DRAFT"));

        AuditEvent response = auditEventRowMapper.mapRow(resultSet, 0);

        assertThat(response.action()).isEqualTo(AuditAction.EXPERIMENT_CREATED);
        assertThat(response.actor().userId()).isEqualTo(userId);
        assertThat(response.target().id()).isEqualTo(targetId);
    }

    @Test
    void mapRow_shouldMapSystemActorEvent() throws SQLException {
        UUID targetId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-04-12T10:00:00Z");
        AuditEventRowMapper auditEventRowMapper = new AuditEventRowMapper(auditDetailsJsonSerializer);
        when(resultSet.getString("action")).thenReturn(AuditAction.EXPERIMENT_UPDATED.name());
        when(resultSet.getString("actor_type")).thenReturn(AuditActorType.SYSTEM.name());
        when(resultSet.getString("system_actor_name")).thenReturn("scheduler");
        when(resultSet.getString("target_type")).thenReturn(AuditTargetType.FEATURE_FLAG.name());
        when(resultSet.getObject("target_id", UUID.class)).thenReturn(targetId);
        when(resultSet.getTimestamp("event_timestamp")).thenReturn(Timestamp.from(timestamp));
        when(resultSet.getString("details")).thenReturn("{\"state\":\"RUNNING\"}");
        when(auditDetailsJsonSerializer.deserialize("{\"state\":\"RUNNING\"}"))
                .thenReturn(AuditDetails.entry("state", "RUNNING"));

        AuditEvent response = auditEventRowMapper.mapRow(resultSet, 0);

        assertThat(response.actor().systemName()).isEqualTo("scheduler");
        assertThat(response.target().type()).isEqualTo(AuditTargetType.FEATURE_FLAG);
    }

    @Test
    void mapRow_shouldRejectUnknownEnumValue() throws SQLException {
        AuditEventRowMapper auditEventRowMapper = new AuditEventRowMapper(auditDetailsJsonSerializer);
        when(resultSet.getString("action")).thenReturn("UNKNOWN");

        assertThatThrownBy(() -> auditEventRowMapper.mapRow(resultSet, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown enum value 'UNKNOWN' for 'AuditAction'");
    }
}
