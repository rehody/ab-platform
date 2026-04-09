package io.github.rehody.abplatform.validation;

import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ExperimentVariantConstraintValidator
        implements ConstraintValidator<ValidExperimentVariant, ExperimentVariant> {

    @Override
    public boolean isValid(ExperimentVariant variant, ConstraintValidatorContext context) {
        if (variant == null) {
            return true;
        }

        String normalizedKey = normalizeKey(variant.key());
        if (normalizedKey == null || normalizedKey.isBlank()) {
            return false;
        }
        if (variant.type() == null) {
            return false;
        }
        if (!hasValidWeight(variant)) {
            return false;
        }

        return isValidFeatureValue(variant.value());
    }

    private boolean hasValidWeight(ExperimentVariant variant) {
        if (variant.isControl()) {
            return variant.weight() == null;
        }

        if (!variant.isRegular()) {
            return false;
        }

        if (variant.weight() == null) {
            return false;
        }

        return variant.weight().signum() > 0;
    }

    private boolean isValidFeatureValue(FeatureValue featureValue) {
        return featureValue != null && featureValue.hasMatchingType();
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return null;
        }

        return key.trim();
    }
}
