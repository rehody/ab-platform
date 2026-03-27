package io.github.rehody.abplatform.repository.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentVariantPreparerTest {

    private final ExperimentVariantPreparer experimentVariantPreparer = new ExperimentVariantPreparer();

    @Test
    void prepare_shouldTrimKeysReindexPositionsAndGenerateMissingIds() {
        UUID existingId = UUID.randomUUID();
        List<ExperimentVariant> prepared = experimentVariantPreparer.prepare(
                UUID.randomUUID(),
                List.of(
                        new ExperimentVariant(null, " control ", new FeatureValue(true, FeatureValueType.BOOL), 100),
                        new ExperimentVariant(
                                existingId, "variant-a", new FeatureValue("blue", FeatureValueType.STRING), 200)));

        assertThat(prepared).hasSize(2);
        assertThat(prepared.get(0).id()).isNotNull();
        assertThat(prepared.get(0).key()).isEqualTo("control");
        assertThat(prepared.get(0).position()).isZero();
        assertThat(prepared.get(1).id()).isEqualTo(existingId);
        assertThat(prepared.get(1).position()).isEqualTo(1);
    }

    @Test
    void prepare_shouldThrowIllegalArgumentExceptionWhenDuplicateKeysDetectedAfterNormalization() {
        UUID experimentId = UUID.randomUUID();

        assertThatThrownBy(() -> experimentVariantPreparer.prepare(
                        experimentId,
                        List.of(
                                new ExperimentVariant(
                                        null, "control", new FeatureValue(true, FeatureValueType.BOOL), 0),
                                new ExperimentVariant(
                                        null, " control ", new FeatureValue(false, FeatureValueType.BOOL), 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate variant key for experiment %s: control".formatted(experimentId));
    }
}
