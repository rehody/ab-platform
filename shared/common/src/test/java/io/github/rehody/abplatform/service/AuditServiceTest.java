package io.github.rehody.abplatform.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditTarget;
import io.github.rehody.abplatform.repository.AuditEventRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditEventRepository);
    }

    @Test
    void write_shouldPersistAuditEvent() {
        auditService.write(
                AuditActor.system("scheduler"),
                AuditAction.EXPERIMENT_CREATED,
                AuditTarget.experiment(UUID.randomUUID()),
                AuditDetails.entry("state", "DRAFT"));

        verify(auditEventRepository).save(any());
    }
}
