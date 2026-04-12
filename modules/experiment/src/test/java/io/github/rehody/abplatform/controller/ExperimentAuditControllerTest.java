package io.github.rehody.abplatform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.dto.response.ExperimentAuditEventResponse;
import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditEvent;
import io.github.rehody.abplatform.model.audit.AuditTarget;
import io.github.rehody.abplatform.service.ExperimentAuditQueryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentAuditControllerTest {

    @Mock
    private ExperimentAuditQueryService experimentAuditQueryService;

    private ExperimentAuditController experimentAuditController;

    @BeforeEach
    void setUp() {
        experimentAuditController = new ExperimentAuditController(experimentAuditQueryService);
    }

    @Test
    void getHistory_shouldMapAuditEventsToResponse() {
        UUID experimentId = UUID.randomUUID();
        AuditEvent auditEvent = new AuditEvent(
                AuditAction.EXPERIMENT_CREATED,
                AuditActor.system("scheduler"),
                AuditTarget.experiment(experimentId),
                Instant.parse("2026-04-12T10:00:00Z"),
                AuditDetails.entry("state", "DRAFT"));
        when(experimentAuditQueryService.getHistory(experimentId)).thenReturn(List.of(auditEvent));

        List<ExperimentAuditEventResponse> response = experimentAuditController.getHistory(experimentId);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().action()).isEqualTo(AuditAction.EXPERIMENT_CREATED);
        verify(experimentAuditQueryService).getHistory(experimentId);
    }
}
