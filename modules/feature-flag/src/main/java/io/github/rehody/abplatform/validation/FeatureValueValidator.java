package io.github.rehody.abplatform.validation;

import io.github.rehody.abplatform.model.FeatureValue;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class FeatureValueValidator implements ConstraintValidator<ValidFeatureValue, FeatureValue> {

    @Override
    public boolean isValid(FeatureValue featureValue, ConstraintValidatorContext context) {
        if (featureValue == null) {
            return true;
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
