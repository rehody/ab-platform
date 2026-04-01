package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import java.util.UUID;

public record CachedFeatureFlag(UUID id, String key, FeatureValue defaultValue, long version) {

    public static CachedFeatureFlag from(FeatureFlag featureFlag) {
        return new CachedFeatureFlag(
                featureFlag.id(), featureFlag.key(), featureFlag.defaultValue(), featureFlag.version());
    }

    public FeatureFlagResponse toResponse() {
        return new FeatureFlagResponse(key, defaultValue, version);
    }

    public FeatureFlag toModel() {
        return new FeatureFlag(id, key, defaultValue, version);
    }
}
