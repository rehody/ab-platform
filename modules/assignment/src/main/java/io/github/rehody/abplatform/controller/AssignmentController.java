package io.github.rehody.abplatform.controller;

import io.github.rehody.abplatform.dto.request.AssignmentRequest;
import io.github.rehody.abplatform.dto.response.AssignmentResponse;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @PostMapping("/resolve")
    public AssignmentResponse resolve(@Valid @RequestBody AssignmentRequest request) {
        FeatureValue value = assignmentService.resolve(request.userId(), request.flagKey());
        return AssignmentResponse.of(value);
    }
}
