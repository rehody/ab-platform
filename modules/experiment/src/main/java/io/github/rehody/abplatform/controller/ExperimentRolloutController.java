package io.github.rehody.abplatform.controller;

import io.github.rehody.abplatform.dto.request.ExperimentRolloutActionRequest;
import io.github.rehody.abplatform.dto.response.ExperimentRolloutResponse;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import io.github.rehody.abplatform.service.ExperimentQueryService;
import io.github.rehody.abplatform.service.ExperimentRuntimeService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
public class ExperimentRolloutController {

    private final ExperimentQueryService experimentQueryService;
    private final ExperimentRuntimeService experimentRuntimeService;

    @GetMapping("/{id}/rollout")
    @RequiresPlatformPermission(PlatformPermission.VIEW_ROLLOUT_STATE)
    public ExperimentRolloutResponse get(@PathVariable UUID id) {
        Experiment experiment = experimentQueryService.getById(id);
        return ExperimentRolloutResponse.from(experiment);
    }

    @PostMapping("/{id}/rollout/advance")
    @RequiresPlatformPermission(PlatformPermission.ADVANCE_EXPERIMENT_ROLLOUT)
    public ExperimentRolloutResponse advance(
            Principal principal, @PathVariable UUID id, @Valid @RequestBody ExperimentRolloutActionRequest request) {
        Experiment experiment =
                experimentRuntimeService.advanceRollout(id, request.version(), AuditActor.user(principal.getName()));
        return ExperimentRolloutResponse.from(experiment);
    }

    @PostMapping("/{id}/rollout/rollback")
    @RequiresPlatformPermission(PlatformPermission.ROLLBACK_EXPERIMENT_ROLLOUT)
    public ExperimentRolloutResponse rollback(
            Principal principal, @PathVariable UUID id, @Valid @RequestBody ExperimentRolloutActionRequest request) {
        Experiment experiment =
                experimentRuntimeService.rollbackRollout(id, request.version(), AuditActor.user(principal.getName()));
        return ExperimentRolloutResponse.from(experiment);
    }
}
