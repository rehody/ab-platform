package io.github.rehody.abplatform.dto.request;

import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.validation.ValidFeatureValue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record FeatureFlagUpdateRequest(
        @NotNull(message = "defaultValue is required") @ValidFeatureValue
        FeatureValue defaultValue,

        @NotNull(message = "version is required") @PositiveOrZero(message = "version must be >= 0") Long version) {}
