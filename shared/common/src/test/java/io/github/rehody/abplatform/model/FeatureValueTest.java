package io.github.rehody.abplatform.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import org.junit.jupiter.api.Test;

class FeatureValueTest {

    @Test
    void constructor_shouldStoreValueAndTypeAndProvideRecordAccessors() {
        FeatureValue featureValue = new FeatureValue("variant-a", FeatureValueType.STRING);

        assertThat(featureValue.value()).isEqualTo("variant-a");
        assertThat(featureValue.type()).isEqualTo(FeatureValueType.STRING);
    }

    @Test
    void values_shouldContainAllSupportedTypesAndKeepDeclaredOrder() {
        FeatureValueType[] featureValueTypes = FeatureValueType.values();

        assertThat(featureValueTypes)
                .containsExactly(FeatureValueType.NUMBER, FeatureValueType.STRING, FeatureValueType.BOOL);
    }

    @Test
    void hasMatchingType_shouldReturnTrueForCompatibleValue() {
        FeatureValue featureValue = new FeatureValue("variant-a", FeatureValueType.STRING);

        assertThat(featureValue.hasMatchingType()).isTrue();
    }

    @Test
    void hasMatchingType_shouldReturnFalseForNullTypeOrValue() {
        assertThat(new FeatureValue("variant-a", null).hasMatchingType()).isFalse();
        assertThat(new FeatureValue(null, FeatureValueType.STRING).hasMatchingType())
                .isFalse();
    }

    @Test
    void hasMatchingType_shouldReturnFalseForIncompatibleValue() {
        FeatureValue featureValue = new FeatureValue(10, FeatureValueType.STRING);

        assertThat(featureValue.hasMatchingType()).isFalse();
    }
}
