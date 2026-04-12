package io.github.rehody.abplatform.controller;

import io.github.rehody.abplatform.dto.request.ExperimentCreateRequest;
import io.github.rehody.abplatform.dto.request.ExperimentUpdateRequest;
import io.github.rehody.abplatform.dto.response.ExperimentResponse;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import io.github.rehody.abplatform.service.ExperimentDraftService;
import io.github.rehody.abplatform.service.ExperimentQueryService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentDraftService experimentDraftService;
    private final ExperimentQueryService experimentQueryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPlatformPermission(PlatformPermission.CREATE_EXPERIMENT)
    public ExperimentResponse create(Principal principal, @Valid @RequestBody ExperimentCreateRequest request) {
        Experiment experiment = experimentDraftService.create(
                AuditActor.user(principal.getName()),
                request.flagKey(),
                request.domainKey(),
                request.variants(),
                request.state());
        return ExperimentResponse.from(experiment);
    }

    @PatchMapping("/{id}")
    @RequiresPlatformPermission(PlatformPermission.UPDATE_EXPERIMENT_DRAFT)
    public ExperimentResponse update(
            Principal principal, @PathVariable UUID id, @Valid @RequestBody ExperimentUpdateRequest request) {
        Experiment experiment = experimentDraftService.update(
                AuditActor.user(principal.getName()),
                id,
                request.flagKey(),
                request.domainKey(),
                request.variants(),
                request.version());
        return ExperimentResponse.from(experiment);
    }

    @GetMapping("/{id}")
    @RequiresPlatformPermission(PlatformPermission.VIEW_EXPERIMENTS)
    public ExperimentResponse get(@PathVariable UUID id) {
        Experiment experiment = experimentQueryService.getById(id);
        return ExperimentResponse.from(experiment);
    }

    @GetMapping
    @RequiresPlatformPermission(PlatformPermission.VIEW_EXPERIMENTS)
    public List<ExperimentResponse> getAll() {
        List<Experiment> experiments = experimentQueryService.getAll();
        return experiments.stream().map(ExperimentResponse::from).toList();
    }
}
