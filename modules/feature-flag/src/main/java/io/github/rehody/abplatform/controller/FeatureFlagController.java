package io.github.rehody.abplatform.controller;

import io.github.rehody.abplatform.dto.request.FeatureFlagCreateRequest;
import io.github.rehody.abplatform.dto.request.FeatureFlagUpdateRequest;
import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.service.FeatureFlagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/flags")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @PostMapping
    public FeatureFlagResponse create(@Valid @RequestBody FeatureFlagCreateRequest request) {
        return featureFlagService.create(request);
    }

    @PutMapping("{key}")
    public FeatureFlagResponse update(@PathVariable String key, @Valid @RequestBody FeatureFlagUpdateRequest request) {
        return featureFlagService.update(key, request);
    }

    @GetMapping("{key}")
    public FeatureFlagResponse get(@PathVariable String key) {
        return featureFlagService.getByKey(key);
    }
}
