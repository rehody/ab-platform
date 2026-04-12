package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditEvent;
import io.github.rehody.abplatform.model.audit.AuditTarget;
import io.github.rehody.abplatform.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Transactional
    public void write(AuditActor actor, AuditAction action, AuditTarget target, AuditDetails details) {
        auditEventRepository.save(AuditEvent.create(action, actor, target, details));
    }
}
