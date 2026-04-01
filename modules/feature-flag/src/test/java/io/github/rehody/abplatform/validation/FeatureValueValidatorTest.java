package io.github.rehody.abplatform.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import org.junit.jupiter.api.Test;

class FeatureValueValidatorTest {

    private final FeatureValueValidator validator = new FeatureValueValidator();

    @Test
    void isValid_shouldReturnTrueAndAllowNullFeatureValue() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullType() {
        FeatureValue featureValue = new FeatureValue(true, null);

        assertThat(validator.isValid(featureValue, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullValue() {
        FeatureValue featureValue = new FeatureValue(null, FeatureValueType.BOOL);

        assertThat(validator.isValid(featureValue, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrueAndAcceptBooleanForBoolType() {
        FeatureValue featureValue = new FeatureValue(true, FeatureValueType.BOOL);

        assertThat(validator.isValid(featureValue, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNonBooleanForBoolType() {
        FeatureValue featureValue = new FeatureValue("true", FeatureValueType.BOOL);

        assertThat(validator.isValid(featureValue, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrueAndAcceptStringForStringType() {
        FeatureValue featureValue = new FeatureValue("on", FeatureValueType.STRING);

        assertThat(validator.isValid(featureValue, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNonStringForStringType() {
        FeatureValue featureValue = new FeatureValue(11, FeatureValueType.STRING);

        assertThat(validator.isValid(featureValue, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrueAndAcceptNumberForNumberType() {
        FeatureValue featureValue = new FeatureValue(42.5d, FeatureValueType.NUMBER);

        assertThat(validator.isValid(featureValue, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNonNumberForNumberType() {
        FeatureValue featureValue = new FeatureValue(false, FeatureValueType.NUMBER);

        assertThat(validator.isValid(featureValue, null)).isFalse();
    }
}
