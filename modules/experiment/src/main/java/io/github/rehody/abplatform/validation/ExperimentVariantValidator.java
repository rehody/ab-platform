package io.github.rehody.abplatform.validation;

import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ExperimentVariantValidator implements ConstraintValidator<ValidExperimentVariant, ExperimentVariant> {

    @Override
    public boolean isValid(ExperimentVariant variant, ConstraintValidatorContext context) {
        if (variant == null) {
            return true;
        }

        String normalizedKey = normalizeKey(variant.key());
        if (normalizedKey == null || normalizedKey.isBlank()) {
            return false;
        }
        if (variant.weight() == null || variant.weight().signum() <= 0) {
            return false;
        }
        if (variant.variantType() == null) {
            return false;
        }

        return isValidFeatureValue(variant.value());
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
