package io.github.rehody.abplatform.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentResponseTest {

    @Test
    void from_shouldMapAllFieldsFromExperiment() {
        ExperimentVariant variant =
                new ExperimentVariant(UUID.randomUUID(), "control", new FeatureValue(true, FeatureValueType.BOOL), 0);
        Experiment experiment =
                new Experiment(UUID.randomUUID(), "checkout-redesign", List.of(variant), ExperimentState.APPROVED, 7L);

        ExperimentResponse response = ExperimentResponse.from(experiment);

        assertThat(response.flagKey()).isEqualTo("checkout-redesign");
        assertThat(response.variants()).containsExactly(variant);
        assertThat(response.state()).isEqualTo(ExperimentState.APPROVED);
        assertThat(response.version()).isEqualTo(7L);
    }
}
