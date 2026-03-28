package io.github.rehody.abplatform.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ExperimentVariant(UUID id, String key, FeatureValue value, int position, BigDecimal weight) {}
