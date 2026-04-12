package io.github.rehody.abplatform.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditEvent;
import io.github.rehody.abplatform.model.audit.AuditTarget;
import io.github.rehody.abplatform.model.audit.AuditTargetType;
import io.github.rehody.abplatform.repository.mapper.AuditDetailsJsonSerializer;
import io.github.rehody.abplatform.repository.rowmapper.AuditEventRowMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class AuditEventRepositoryTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private AuditDetailsJsonSerializer auditDetailsJsonSerializer;

    @Mock
    private AuditEventRowMapper auditEventRowMapper;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<AuditEvent> mappedQuerySpec;

    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void setUp() {
        auditEventRepository = new AuditEventRepository(jdbcClient, auditDetailsJsonSerializer, auditEventRowMapper);
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
    }

    @Test
    void save_shouldWriteAuditEventViaJdbcClient() {
        AuditEvent auditEvent = new AuditEvent(
                AuditAction.EXPERIMENT_CREATED,
                AuditActor.system("scheduler"),
                AuditTarget.experiment(UUID.randomUUID()),
                Instant.parse("2026-04-12T10:00:00Z"),
                AuditDetails.entry("state", "DRAFT"));
        when(auditDetailsJsonSerializer.serialize(auditEvent.details())).thenReturn("{\"state\":\"DRAFT\"}");

        auditEventRepository.save(auditEvent);

        verify(statementSpec).update();
        verify(auditDetailsJsonSerializer).serialize(auditEvent.details());
    }

    @Test
    void findByTarget_shouldQueryAuditEventsByTarget() {
        UUID targetId = UUID.randomUUID();
        AuditEvent auditEvent = new AuditEvent(
                AuditAction.EXPERIMENT_CREATED,
                AuditActor.system("scheduler"),
                AuditTarget.experiment(targetId),
                Instant.parse("2026-04-12T10:00:00Z"),
                AuditDetails.entry("state", "DRAFT"));
        when(statementSpec.query(eq(auditEventRowMapper))).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.list()).thenReturn(List.of(auditEvent));

        List<AuditEvent> response = auditEventRepository.findByTarget(AuditTargetType.EXPERIMENT, targetId);

        assertThat(response).containsExactly(auditEvent);
        verify(statementSpec).query(auditEventRowMapper);
    }
}
