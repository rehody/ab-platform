package io.github.rehody.abplatform.repository.rowmapper;

import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditActorType;
import io.github.rehody.abplatform.model.audit.AuditEvent;
import io.github.rehody.abplatform.model.audit.AuditTarget;
import io.github.rehody.abplatform.model.audit.AuditTargetType;
import io.github.rehody.abplatform.repository.mapper.AuditDetailsJsonSerializer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditEventRowMapper implements RowMapper<AuditEvent> {

    private final AuditDetailsJsonSerializer auditDetailsJsonSerializer;

    @Override
    public AuditEvent mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new AuditEvent(
                parseEnum(resultSet.getString("action"), AuditAction.class),
                mapActor(resultSet),
                mapTarget(resultSet),
                resultSet.getTimestamp("event_timestamp").toInstant(),
                auditDetailsJsonSerializer.deserialize(resultSet.getString("details")));
    }

    private AuditActor mapActor(ResultSet resultSet) throws SQLException {
        AuditActorType actorType = parseEnum(resultSet.getString("actor_type"), AuditActorType.class);

        if (actorType == AuditActorType.USER) {
            return AuditActor.user(resultSet.getObject("user_actor_id", UUID.class));
        }

        return AuditActor.system(resultSet.getString("system_actor_name"));
    }

    private AuditTarget mapTarget(ResultSet resultSet) throws SQLException {
        return new AuditTarget(
                parseEnum(resultSet.getString("target_type"), AuditTargetType.class),
                resultSet.getObject("target_id", UUID.class));
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType) {
        for (T enumConstant : enumType.getEnumConstants()) {
            if (enumConstant.toString().equals(value)) {
                return enumConstant;
            }
        }

        throw new IllegalStateException("Unknown enum value '%s' for '%s'".formatted(value, enumType.getSimpleName()));
    }
}
