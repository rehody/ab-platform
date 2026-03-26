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
}
