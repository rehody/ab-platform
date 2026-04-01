package io.github.rehody.abplatform.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentVariantValidatorTest {

    private final ExperimentVariantValidator validator = new ExperimentVariantValidator();

    @Test
    void isValid_shouldReturnTrueAndAllowNullVariant() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectBlankKey() {
        ExperimentVariant variant = regularVariant(" ", validValue());

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullKey() {
        ExperimentVariant variant = regularVariant(null, validValue());

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullValue() {
        ExperimentVariant variant = controlVariant("control", null);

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullFeatureType() {
        ExperimentVariant variant = controlVariant("control", new FeatureValue(true, null));

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullFeatureValue() {
        ExperimentVariant variant = controlVariant("control", new FeatureValue(null, FeatureValueType.BOOL));

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullVariantType() {
        ExperimentVariant variant =
                new ExperimentVariant(UUID.randomUUID(), "control", validValue(), 0, BigDecimal.ONE, null);

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrueAndAcceptControlVariant() {
        ExperimentVariant variant = controlVariant("control", validValue());

        assertThat(validator.isValid(variant, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectWrongBooleanValueType() {
        ExperimentVariant variant = controlVariant("control", new FeatureValue("true", FeatureValueType.BOOL));

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrueAndAcceptStringValue() {
        ExperimentVariant variant = regularVariant("variant-a", new FeatureValue("on", FeatureValueType.STRING));

        assertThat(validator.isValid(variant, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectWrongStringValueType() {
        ExperimentVariant variant = regularVariant("variant-a", new FeatureValue(10, FeatureValueType.STRING));

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrueAndAcceptNumberValue() {
        ExperimentVariant variant = regularVariant("variant-b", new FeatureValue(42, FeatureValueType.NUMBER));

        assertThat(validator.isValid(variant, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectWrongNumberValueType() {
        ExperimentVariant variant = regularVariant("variant-b", new FeatureValue(false, FeatureValueType.NUMBER));

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullWeight() {
        ExperimentVariant variant = controlVariant("variant-a", validValue());
        ExperimentVariant invalidVariant = new ExperimentVariant(
                variant.id(), variant.key(), variant.value(), variant.position(), null, variant.type());

        assertThat(validator.isValid(invalidVariant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNonPositiveWeight() {
        ExperimentVariant variant = new ExperimentVariant(
                UUID.randomUUID(), "control", validValue(), 0, BigDecimal.ZERO, ExperimentVariantType.CONTROL);

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    private FeatureValue validValue() {
        return new FeatureValue(true, FeatureValueType.BOOL);
    }

    private ExperimentVariant controlVariant(String key, FeatureValue value) {
        return new ExperimentVariant(UUID.randomUUID(), key, value, 0, BigDecimal.ONE, ExperimentVariantType.CONTROL);
    }

    private ExperimentVariant regularVariant(String key, FeatureValue value) {
        return new ExperimentVariant(UUID.randomUUID(), key, value, 0, BigDecimal.ONE, ExperimentVariantType.REGULAR);
    }
}
