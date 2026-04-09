package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExperimentVariantValidator {

    public void validate(UUID experimentId, ExperimentVariant variant) {
        if (variant.type() == null) {
            throw new IllegalArgumentException("Variant type is required for experiment %s".formatted(experimentId));
        }

        if (variant.isControl()) {
            if (variant.weight() != null) {
                throw new IllegalArgumentException(
                        "CONTROL variant must not define weight for experiment %s".formatted(experimentId));
            }
            return;
        }

        if (!variant.isRegular()) {
            throw new IllegalArgumentException(
                    "Unexpected variant type %s for experiment %s".formatted(variant.type(), experimentId));
        }

        if (variant.weight() == null || variant.weight().signum() <= 0) {
            throw new IllegalArgumentException(
                    "REGULAR variant must define positive weight for experiment %s".formatted(experimentId));
        }
    }
}
