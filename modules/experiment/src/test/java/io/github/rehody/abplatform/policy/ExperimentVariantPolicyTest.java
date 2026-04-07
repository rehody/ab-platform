package io.github.rehody.abplatform.policy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentVariantPolicyTest {

    private final ExperimentVariantPolicy policy = new ExperimentVariantPolicy();

    @Test
    void validateVariantConfiguration_shouldAcceptValidConfiguration() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        List<ExperimentVariant> variants =
                List.of(controlVariant("control", defaultValue), regularVariant("variant-a", boolValue(false)));

        assertThatCode(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .doesNotThrowAnyException();
    }

    @Test
    void validateVariantConfiguration_shouldAcceptValidStringConfiguration() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = stringValue("blue");
        List<ExperimentVariant> variants =
                List.of(controlVariant("control", defaultValue), regularVariant("variant-a", stringValue("red")));

        assertThatCode(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .doesNotThrowAnyException();
    }

    @Test
    void validateVariantConfiguration_shouldRejectMissingControlVariant() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        List<ExperimentVariant> variants =
                List.of(regularVariant("variant-a", boolValue(false)), regularVariant("variant-b", boolValue(true)));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Experiment %s must contain exactly one CONTROL variant".formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldRejectMultipleControlVariants() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        List<ExperimentVariant> variants = List.of(
                controlVariant("control", defaultValue),
                controlVariant("control", defaultValue),
                regularVariant("variant-a", boolValue(false)));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Experiment %s must contain exactly one CONTROL variant".formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldRejectMissingRegularVariant() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        List<ExperimentVariant> variants = List.of(controlVariant("control", defaultValue));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Experiment %s must contain at least one REGULAR variant".formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldRejectUnexpectedControlKey() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        List<ExperimentVariant> variants =
                List.of(controlVariant("variant-a", defaultValue), regularVariant("variant-b", boolValue(false)));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CONTROL variant key must be 'control' for experiment %s".formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldRejectControlKeyForRegularVariant() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        List<ExperimentVariant> variants =
                List.of(controlVariant("control", defaultValue), regularVariant("control", boolValue(false)));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("REGULAR variant key must not be 'control' for experiment %s".formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldRejectControlValueMismatch() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        List<ExperimentVariant> variants =
                List.of(controlVariant("control", boolValue(false)), regularVariant("variant-a", boolValue(false)));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CONTROL variant value must match default flag value for experiment %s"
                        .formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldRejectControlValueTypeMismatch() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        List<ExperimentVariant> variants =
                List.of(controlVariant("control", stringValue("true")), regularVariant("variant-a", stringValue("on")));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CONTROL variant value must match default flag value for experiment %s"
                        .formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldRejectNullControlValue() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        List<ExperimentVariant> variants =
                List.of(controlVariant("control", null), regularVariant("variant-a", boolValue(false)));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CONTROL variant value must match default flag value for experiment %s"
                        .formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldRejectNullDefaultValue() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants =
                List.of(controlVariant("control", boolValue(true)), regularVariant("variant-a", boolValue(false)));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CONTROL variant value must match default flag value for experiment %s"
                        .formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldRejectDuplicateVariantValues() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        List<ExperimentVariant> variants = List.of(
                controlVariant("control", defaultValue),
                regularVariant("variant-a", boolValue(false)),
                regularVariant("variant-b", boolValue(false)));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate variant value for experiment %s: BOOL:false".formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldRejectDuplicateNormalizedNumberValues() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = numberValue("0");
        List<ExperimentVariant> variants = List.of(
                controlVariant("control", defaultValue),
                regularVariant("variant-a", numberValue("1.0")),
                regularVariant("variant-b", numberValue("1")));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate variant value for experiment %s: NUMBER:1".formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldReportRepeatedDuplicateValueConsistently() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        List<ExperimentVariant> variants = List.of(
                controlVariant("control", defaultValue),
                regularVariant("variant-a", boolValue(false)),
                regularVariant("variant-b", boolValue(false)),
                regularVariant("variant-c", boolValue(false)));

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate variant value for experiment %s: BOOL:false".formatted(experimentId));
    }

    @Test
    void validateResolvableVariantConfiguration_shouldAcceptResolvableConfiguration() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants =
                List.of(controlVariant("control", boolValue(true)), regularVariant("variant-a", boolValue(false)));

        assertThatCode(() -> policy.validateResolvableVariantConfiguration(experimentId, variants))
                .doesNotThrowAnyException();
    }

    @Test
    void validateResolvableVariantConfiguration_shouldRejectMissingRegularVariant() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants = List.of(controlVariant("control", boolValue(true)));

        assertThatThrownBy(() -> policy.validateResolvableVariantConfiguration(experimentId, variants))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Running experiment %s must contain at least one REGULAR variant".formatted(experimentId));
    }

    @Test
    void validateResolvableVariantConfiguration_shouldRejectMissingControlVariant() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants = List.of(regularVariant("variant-a", boolValue(false)));

        assertThatThrownBy(() -> policy.validateResolvableVariantConfiguration(experimentId, variants))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Running experiment %s must contain exactly one CONTROL variant".formatted(experimentId));
    }

    @Test
    void validateVariantConfiguration_shouldRejectUnexpectedRegularVariantType() {
        UUID experimentId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        ExperimentVariant brokenRegular = mock(ExperimentVariant.class);
        when(brokenRegular.isControl()).thenReturn(false);
        when(brokenRegular.isRegular()).thenReturn(false);
        when(brokenRegular.key()).thenReturn("variant-a");
        when(brokenRegular.type()).thenReturn(ExperimentVariantType.CONTROL);
        when(brokenRegular.value()).thenReturn(boolValue(false));

        List<ExperimentVariant> variants = List.of(controlVariant("control", defaultValue), brokenRegular);

        assertThatThrownBy(() -> policy.validateVariantConfiguration(experimentId, variants, defaultValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unexpected variant type CONTROL for experiment %s".formatted(experimentId));
    }

    private FeatureValue boolValue(boolean value) {
        return new FeatureValue(value, FeatureValueType.BOOL);
    }

    private FeatureValue stringValue(String value) {
        return new FeatureValue(value, FeatureValueType.STRING);
    }

    private FeatureValue numberValue(String value) {
        return new FeatureValue(value, FeatureValueType.NUMBER);
    }

    private ExperimentVariant controlVariant(String key, FeatureValue value) {
        return new ExperimentVariant(UUID.randomUUID(), key, value, 0, BigDecimal.ONE, ExperimentVariantType.CONTROL);
    }

    private ExperimentVariant regularVariant(String key, FeatureValue value) {
        return new ExperimentVariant(UUID.randomUUID(), key, value, 1, BigDecimal.ONE, ExperimentVariantType.REGULAR);
    }
}
