package io.github.rehody.abplatform.controller;

import io.github.rehody.abplatform.dto.request.ExperimentStateTransitionRequest;
import io.github.rehody.abplatform.dto.response.ExperimentResponse;
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
    public ExperimentResponse submitForReview(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        return experimentLifecycleService.submitForReview(id, request);
    }

    @PostMapping("/{id}/approve")
    public ExperimentResponse approve(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        return experimentLifecycleService.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    public ExperimentResponse reject(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        return experimentLifecycleService.reject(id, request);
    }

    @PostMapping("/{id}/start")
    public ExperimentResponse start(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        return experimentLifecycleService.start(id, request);
    }

    @PostMapping("/{id}/pause")
    public ExperimentResponse pause(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        return experimentLifecycleService.pause(id, request);
    }

    @PostMapping("/{id}/resume")
    public ExperimentResponse resume(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        return experimentLifecycleService.resume(id, request);
    }

    @PostMapping("/{id}/complete")
    public ExperimentResponse complete(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        return experimentLifecycleService.complete(id, request);
    }

    @PostMapping("/{id}/archive")
    public ExperimentResponse archive(
            @PathVariable UUID id, @Valid @RequestBody ExperimentStateTransitionRequest request) {
        return experimentLifecycleService.archive(id, request);
    }
}
