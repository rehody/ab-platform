package io.github.rehody.abplatform.controller;

import io.github.rehody.abplatform.dto.request.ExperimentStateTransitionRequest;
import io.github.rehody.abplatform.dto.response.ExperimentResponse;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import io.github.rehody.abplatform.service.ExperimentLifecycleService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
public class ExperimentLifecycleController {

    private final ExperimentLifecycleService experimentLifecycleService;

    @PostMapping("/{id}/submit-for-review")
    @RequiresPlatformPermission(PlatformPermission.SUBMIT_EXPERIMENT_FOR_REVIEW)
    public ExperimentResponse submitForReview(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        Experiment experiment = experimentLifecycleService.submitForReview(id, request.version());
        return ExperimentResponse.from(experiment);
    }

    @PostMapping("/{id}/approve")
    @RequiresPlatformPermission(PlatformPermission.APPROVE_EXPERIMENT)
    public ExperimentResponse approve(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        Experiment experiment = experimentLifecycleService.approve(id, request.version());
        return ExperimentResponse.from(experiment);
    }

    @PostMapping("/{id}/reject")
    @RequiresPlatformPermission(PlatformPermission.REJECT_EXPERIMENT)
    public ExperimentResponse reject(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        Experiment experiment = experimentLifecycleService.reject(id, request.version());
        return ExperimentResponse.from(experiment);
    }

    @PostMapping("/{id}/start")
    @RequiresPlatformPermission(PlatformPermission.START_EXPERIMENT)
    public ExperimentResponse start(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        Experiment experiment = experimentLifecycleService.start(id, request.version());
        return ExperimentResponse.from(experiment);
    }

    @PostMapping("/{id}/pause")
    @RequiresPlatformPermission(PlatformPermission.PAUSE_EXPERIMENT)
    public ExperimentResponse pause(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        Experiment experiment = experimentLifecycleService.pause(id, request.version());
        return ExperimentResponse.from(experiment);
    }

    @PostMapping("/{id}/resume")
    @RequiresPlatformPermission(PlatformPermission.RESUME_EXPERIMENT)
    public ExperimentResponse resume(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        Experiment experiment = experimentLifecycleService.resume(id, request.version());
        return ExperimentResponse.from(experiment);
    }

    @PostMapping("/{id}/complete")
    @RequiresPlatformPermission(PlatformPermission.COMPLETE_EXPERIMENT)
    public ExperimentResponse complete(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        Experiment experiment = experimentLifecycleService.complete(id, request.version());
        return ExperimentResponse.from(experiment);
    }

    @PostMapping("/{id}/archive")
    @RequiresPlatformPermission(PlatformPermission.ARCHIVE_EXPERIMENT)
    public ExperimentResponse archive(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        Experiment experiment = experimentLifecycleService.archive(id, request.version());
        return ExperimentResponse.from(experiment);
    }
}
