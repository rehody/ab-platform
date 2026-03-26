package io.github.rehody.abplatform.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeatureFlagResponseTest {

    @Test
    void from_shouldMapKeyDefaultValueAndVersionFromFeatureFlag() {
        FeatureValue value = new FeatureValue(true, FeatureValueType.BOOL);
        FeatureFlag featureFlag = new FeatureFlag(UUID.randomUUID(), "new-ui", value, 7L);

        FeatureFlagResponse response = FeatureFlagResponse.from(featureFlag);

        assertThat(response.key()).isEqualTo("new-ui");
        assertThat(response.defaultValue()).isEqualTo(value);
        assertThat(response.version()).isEqualTo(7L);
    }
}
