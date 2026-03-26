package io.github.rehody.abplatform.model;

import java.util.UUID;

public record ExperimentVariant(UUID id, String key, FeatureValue value, int position) {}
