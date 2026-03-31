package io.github.rehody.abplatform.controller;

import io.github.rehody.abplatform.dto.request.FeatureFlagCreateRequest;
import io.github.rehody.abplatform.dto.request.FeatureFlagUpdateRequest;
import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.service.FeatureFlagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/flags")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeatureFlagResponse create(@Valid @RequestBody FeatureFlagCreateRequest request) {
        FeatureFlag featureFlag = featureFlagService.create(request.key(), request.defaultValue());
        return FeatureFlagResponse.from(featureFlag);
    }

    @PutMapping("/{key}")
    public FeatureFlagResponse update(@PathVariable String key, @Valid @RequestBody FeatureFlagUpdateRequest request) {
        FeatureFlag featureFlag = featureFlagService.update(key, request.defaultValue(), request.version());
        return FeatureFlagResponse.from(featureFlag);
    }

    @GetMapping("/{key}")
    public FeatureFlagResponse get(@PathVariable String key) {
        FeatureFlag featureFlag = featureFlagService.getByKey(key);
        return FeatureFlagResponse.from(featureFlag);
    }
}
