package io.github.rehody.abplatform.model;

import io.github.rehody.abplatform.enums.ExperimentVariantType;
import java.math.BigDecimal;
import java.util.UUID;

public record ExperimentVariant(
        UUID id, String key, FeatureValue value, int position, BigDecimal weight, ExperimentVariantType type) {

    public boolean isControl() {
        return type == ExperimentVariantType.CONTROL;
    }

    public boolean isRegular() {
        return type == ExperimentVariantType.REGULAR;
    }
}
