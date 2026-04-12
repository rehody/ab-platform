package io.github.rehody.abplatform.controller;

import io.github.rehody.abplatform.dto.request.FeatureFlagCreateRequest;
import io.github.rehody.abplatform.dto.request.FeatureFlagUpdateRequest;
import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import io.github.rehody.abplatform.service.FeatureFlagService;
import jakarta.validation.Valid;
import java.security.Principal;
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
    @RequiresPlatformPermission(PlatformPermission.CREATE_FEATURE_FLAG)
    public FeatureFlagResponse create(Principal principal, @Valid @RequestBody FeatureFlagCreateRequest request) {
        FeatureFlag featureFlag =
                featureFlagService.create(AuditActor.user(principal.getName()), request.key(), request.defaultValue());
        return FeatureFlagResponse.from(featureFlag);
    }

    @PutMapping("/{key}")
    @RequiresPlatformPermission(PlatformPermission.UPDATE_FEATURE_FLAG)
    public FeatureFlagResponse update(
            Principal principal, @PathVariable String key, @Valid @RequestBody FeatureFlagUpdateRequest request) {
        FeatureFlag featureFlag = featureFlagService.update(
                AuditActor.user(principal.getName()), key, request.defaultValue(), request.version());
        return FeatureFlagResponse.from(featureFlag);
    }

    @GetMapping("/{key}")
    @RequiresPlatformPermission(PlatformPermission.VIEW_FEATURE_FLAGS)
    public FeatureFlagResponse get(@PathVariable String key) {
        FeatureFlag featureFlag = featureFlagService.getByKey(key);
        return FeatureFlagResponse.from(featureFlag);
    }
}
