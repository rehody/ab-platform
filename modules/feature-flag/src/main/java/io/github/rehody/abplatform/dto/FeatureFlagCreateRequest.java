package io.github.rehody.abplatform.dto;

import io.github.rehody.abplatform.model.FeatureValue;

public record FeatureFlagCreateRequest(String key, FeatureValue defaultValue) {}
