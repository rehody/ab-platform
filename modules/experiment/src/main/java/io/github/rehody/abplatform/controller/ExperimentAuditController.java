package io.github.rehody.abplatform.controller;

import io.github.rehody.abplatform.dto.response.ExperimentAuditEventResponse;
import io.github.rehody.abplatform.model.audit.AuditEvent;
import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import io.github.rehody.abplatform.service.ExperimentAuditQueryService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
public class ExperimentAuditController {

    private final ExperimentAuditQueryService experimentAuditQueryService;

    @GetMapping("/{id}/history")
    @RequiresPlatformPermission(PlatformPermission.VIEW_EXPERIMENT_HISTORY)
    public List<ExperimentAuditEventResponse> getHistory(@PathVariable UUID id) {
        List<AuditEvent> auditEvents = experimentAuditQueryService.getHistory(id);
        return auditEvents.stream().map(ExperimentAuditEventResponse::from).toList();
    }
}
