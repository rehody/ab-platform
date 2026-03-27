package io.github.rehody.abplatform.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
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
        ExperimentVariant variant = new ExperimentVariant(UUID.randomUUID(), " ", validValue(), 0);

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullKey() {
        ExperimentVariant variant = new ExperimentVariant(UUID.randomUUID(), null, validValue(), 0);

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullValue() {
        ExperimentVariant variant = new ExperimentVariant(UUID.randomUUID(), "control", null, 0);

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullFeatureType() {
        ExperimentVariant variant =
                new ExperimentVariant(UUID.randomUUID(), "control", new FeatureValue(true, null), 0);

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectNullFeatureValue() {
        ExperimentVariant variant =
                new ExperimentVariant(UUID.randomUUID(), "control", new FeatureValue(null, FeatureValueType.BOOL), 0);

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrueAndAcceptBooleanValue() {
        ExperimentVariant variant = new ExperimentVariant(UUID.randomUUID(), "control", validValue(), 0);

        assertThat(validator.isValid(variant, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectWrongBooleanValueType() {
        ExperimentVariant variant =
                new ExperimentVariant(UUID.randomUUID(), "control", new FeatureValue("true", FeatureValueType.BOOL), 0);

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrueAndAcceptStringValue() {
        ExperimentVariant variant = new ExperimentVariant(
                UUID.randomUUID(), "variant-a", new FeatureValue("on", FeatureValueType.STRING), 1);

        assertThat(validator.isValid(variant, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectWrongStringValueType() {
        ExperimentVariant variant =
                new ExperimentVariant(UUID.randomUUID(), "variant-a", new FeatureValue(10, FeatureValueType.STRING), 1);

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrueAndAcceptNumberValue() {
        ExperimentVariant variant =
                new ExperimentVariant(UUID.randomUUID(), "variant-b", new FeatureValue(42, FeatureValueType.NUMBER), 2);

        assertThat(validator.isValid(variant, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseAndRejectWrongNumberValueType() {
        ExperimentVariant variant = new ExperimentVariant(
                UUID.randomUUID(), "variant-b", new FeatureValue(false, FeatureValueType.NUMBER), 2);

        assertThat(validator.isValid(variant, null)).isFalse();
    }

    private FeatureValue validValue() {
        return new FeatureValue(true, FeatureValueType.BOOL);
    }
}
