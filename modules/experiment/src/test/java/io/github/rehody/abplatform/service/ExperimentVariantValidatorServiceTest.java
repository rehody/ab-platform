package io.github.rehody.abplatform.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentVariantValidatorServiceTest {

    private final ExperimentVariantValidator experimentVariantValidator = new ExperimentVariantValidator();

    @Test
    void validate_shouldAcceptControlVariantWithoutWeight() {
        assertThatCode(() -> experimentVariantValidator.validate(UUID.randomUUID(), controlVariant(null)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_shouldRejectMissingVariantType() {
        UUID experimentId = UUID.randomUUID();
        ExperimentVariant variant = new ExperimentVariant(
                UUID.randomUUID(), "control", new FeatureValue(true, FeatureValueType.BOOL), 0, null, null);

        assertThatThrownBy(() -> experimentVariantValidator.validate(experimentId, variant))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Variant type is required for experiment %s".formatted(experimentId));
    }

    @Test
    void validate_shouldRejectControlVariantWithWeight() {
        UUID experimentId = UUID.randomUUID();

        assertThatThrownBy(() -> experimentVariantValidator.validate(experimentId, controlVariant(BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CONTROL variant must not define weight for experiment %s".formatted(experimentId));
    }

    @Test
    void validate_shouldRejectUnexpectedVariantType() {
        UUID experimentId = UUID.randomUUID();
        ExperimentVariant variant = mock(ExperimentVariant.class);

        when(variant.type()).thenReturn(ExperimentVariantType.CONTROL);
        when(variant.isControl()).thenReturn(false);
        when(variant.isRegular()).thenReturn(false);

        assertThatThrownBy(() -> experimentVariantValidator.validate(experimentId, variant))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unexpected variant type CONTROL for experiment %s".formatted(experimentId));
    }

    @Test
    void validate_shouldRejectRegularVariantWithoutPositiveWeight() {
        UUID experimentId = UUID.randomUUID();
        ExperimentVariant variant = new ExperimentVariant(
                UUID.randomUUID(),
                "variant-a",
                new FeatureValue(false, FeatureValueType.BOOL),
                1,
                BigDecimal.ZERO,
                ExperimentVariantType.REGULAR);

        assertThatThrownBy(() -> experimentVariantValidator.validate(experimentId, variant))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("REGULAR variant must define positive weight for experiment %s".formatted(experimentId));
    }

    @Test
    void validate_shouldRejectRegularVariantWithoutWeight() {
        UUID experimentId = UUID.randomUUID();
        ExperimentVariant variant = new ExperimentVariant(
                UUID.randomUUID(),
                "variant-a",
                new FeatureValue(false, FeatureValueType.BOOL),
                1,
                null,
                ExperimentVariantType.REGULAR);

        assertThatThrownBy(() -> experimentVariantValidator.validate(experimentId, variant))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("REGULAR variant must define positive weight for experiment %s".formatted(experimentId));
    }

    @Test
    void validate_shouldAcceptRegularVariantWithPositiveWeight() {
        ExperimentVariant variant = new ExperimentVariant(
                UUID.randomUUID(),
                "variant-a",
                new FeatureValue(false, FeatureValueType.BOOL),
                1,
                BigDecimal.ONE,
                ExperimentVariantType.REGULAR);

        assertThatCode(() -> experimentVariantValidator.validate(UUID.randomUUID(), variant))
                .doesNotThrowAnyException();
    }

    private ExperimentVariant controlVariant(BigDecimal weight) {
        return new ExperimentVariant(
                UUID.randomUUID(),
                "control",
                new FeatureValue(true, FeatureValueType.BOOL),
                0,
                weight,
                ExperimentVariantType.CONTROL);
    }
}
