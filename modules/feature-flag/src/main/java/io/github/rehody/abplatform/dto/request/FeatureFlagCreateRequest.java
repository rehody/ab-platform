package io.github.rehody.abplatform.dto.request;

import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.validation.ValidFeatureValue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FeatureFlagCreateRequest(
        @NotBlank(message = "key is required") String key,

        @NotNull(message = "defaultValue is required") @ValidFeatureValue
        FeatureValue defaultValue) {}
