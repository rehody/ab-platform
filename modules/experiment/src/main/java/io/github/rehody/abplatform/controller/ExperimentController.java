package io.github.rehody.abplatform.controller;

import io.github.rehody.abplatform.dto.request.ExperimentCreateRequest;
import io.github.rehody.abplatform.dto.request.ExperimentUpdateRequest;
import io.github.rehody.abplatform.dto.response.ExperimentResponse;
import io.github.rehody.abplatform.service.ExperimentService;
import jakarta.validation.Valid;
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

    private final ExperimentService experimentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExperimentResponse create(@Valid @RequestBody ExperimentCreateRequest request) {
        return experimentService.create(request);
    }

    @PatchMapping("/{id}")
    public ExperimentResponse update(@PathVariable UUID id, @Valid @RequestBody ExperimentUpdateRequest request) {
        return experimentService.update(id, request);
    }

    @GetMapping("/{id}")
    public ExperimentResponse get(@PathVariable UUID id) {
        return experimentService.getById(id);
    }

    @GetMapping
    public List<ExperimentResponse> getAll() {
        return experimentService.getAll();
    }
}
