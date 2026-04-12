package io.github.rehody.abplatform.risk.controller;

import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.risk.dto.request.ExperimentMetricRiskResolutionRequest;
import io.github.rehody.abplatform.risk.dto.response.ExperimentMetricRiskResponse;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.risk.service.ExperimentMetricRiskService;
import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiment-risks")
@RequiredArgsConstructor
public class ExperimentMetricRiskController {

    private final ExperimentMetricRiskService experimentMetricRiskService;

    @PostMapping("/{riskId}/resolve")
    @RequiresPlatformPermission(PlatformPermission.RESOLVE_EXPERIMENT_RISK)
    public ExperimentMetricRiskResponse resolve(
            Principal principal,
            @PathVariable UUID riskId,
            @Valid @RequestBody(required = false) ExperimentMetricRiskResolutionRequest request) {
        String comment = request == null ? null : request.comment();
        ExperimentMetricRisk risk =
                experimentMetricRiskService.resolve(AuditActor.user(principal.getName()), riskId, comment);
        return ExperimentMetricRiskResponse.from(risk);
    }
}
