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

        if (variant.key() == null || variant.key().isBlank()) {
            return false;
        }
        if (variant.weight() == null || variant.weight().signum() <= 0) {
            return false;
        }

        return isValidFeatureValue(variant.value());
    }

    private boolean isValidFeatureValue(FeatureValue featureValue) {
        if (featureValue == null) {
            return false;
        }
        if (featureValue.type() == null || featureValue.value() == null) {
            return false;
        }

        return switch (featureValue.type()) {
            case BOOL -> featureValue.value() instanceof Boolean;
            case STRING -> featureValue.value() instanceof String;
            case NUMBER -> featureValue.value() instanceof Number;
        };
    }
}
