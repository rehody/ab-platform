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

        return featureValue.hasMatchingType();
    }
}
