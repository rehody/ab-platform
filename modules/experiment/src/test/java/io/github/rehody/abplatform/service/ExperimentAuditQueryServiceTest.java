package io.github.rehody.abplatform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditEvent;
import io.github.rehody.abplatform.model.audit.AuditTarget;
import io.github.rehody.abplatform.model.audit.AuditTargetType;
import io.github.rehody.abplatform.repository.AuditEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentAuditQueryServiceTest {

    @Mock
    private ExperimentQueryService experimentQueryService;

    @Mock
    private AuditEventRepository auditEventRepository;

    private ExperimentAuditQueryService experimentAuditQueryService;

    @BeforeEach
    void setUp() {
        experimentAuditQueryService = new ExperimentAuditQueryService(experimentQueryService, auditEventRepository);
    }

    @Test
    void getHistory_shouldEnsureExperimentExistsAndReturnHistory() {
        UUID experimentId = UUID.randomUUID();
        AuditEvent auditEvent = new AuditEvent(
                AuditAction.EXPERIMENT_CREATED,
                AuditActor.user(UUID.randomUUID()),
                AuditTarget.experiment(experimentId),
                Instant.parse("2026-04-12T10:00:00Z"),
                AuditDetails.entry("state", "DRAFT"));
        when(auditEventRepository.findByTarget(AuditTargetType.EXPERIMENT, experimentId))
                .thenReturn(List.of(auditEvent));

        List<AuditEvent> response = experimentAuditQueryService.getHistory(experimentId);

        assertThat(response).containsExactly(auditEvent);
        verify(experimentQueryService).ensureExistsById(experimentId);
        verify(auditEventRepository).findByTarget(AuditTargetType.EXPERIMENT, experimentId);
    }
}
