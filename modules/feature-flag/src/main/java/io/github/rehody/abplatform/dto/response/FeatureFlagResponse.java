package io.github.rehody.abplatform.dto.response;

import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;

public record FeatureFlagResponse(String key, FeatureValue defaultValue) {
    public static FeatureFlagResponse from(FeatureFlag featureFlag) {
        return new FeatureFlagResponse(featureFlag.key(), featureFlag.defaultValue());
    }
}
