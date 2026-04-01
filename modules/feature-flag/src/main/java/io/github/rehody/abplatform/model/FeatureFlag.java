package io.github.rehody.abplatform.model;

import java.util.UUID;

public record FeatureFlag(UUID id, String key, FeatureValue defaultValue, long version) {}
