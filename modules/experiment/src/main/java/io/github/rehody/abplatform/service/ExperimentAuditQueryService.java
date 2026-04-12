package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.model.audit.AuditEvent;
import io.github.rehody.abplatform.model.audit.AuditTargetType;
import io.github.rehody.abplatform.repository.AuditEventRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentAuditQueryService {

    private final ExperimentQueryService experimentQueryService;
    private final AuditEventRepository auditEventRepository;

    @Transactional(readOnly = true)
    public List<AuditEvent> getHistory(UUID experimentId) {
        experimentQueryService.ensureExistsById(experimentId);
        return auditEventRepository.findByTarget(AuditTargetType.EXPERIMENT, experimentId);
    }
}
