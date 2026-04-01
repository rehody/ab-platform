package io.github.rehody.abplatform.repository.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentAggregateMapperTest {

    private final ExperimentAggregateMapper experimentAggregateMapper = new ExperimentAggregateMapper();

    @Test
    void withVariants_shouldCopyVariantsAndPreserveOtherExperimentFields() {
        Experiment experiment =
                new Experiment(UUID.randomUUID(), "flag-a", List.of(), ExperimentState.RUNNING, 5L, null, null);
        List<ExperimentVariant> variants = new ArrayList<>(List.of(new ExperimentVariant(
                UUID.randomUUID(),
                "control",
                new FeatureValue(true, FeatureValueType.BOOL),
                0,
                BigDecimal.ONE,
                ExperimentVariantType.CONTROL)));

        Experiment result = experimentAggregateMapper.withVariants(experiment, variants);

        assertThat(result.id()).isEqualTo(experiment.id());
        assertThat(result.flagKey()).isEqualTo("flag-a");
        assertThat(result.variants()).containsExactlyElementsOf(variants);
        assertThat(result.variants()).isNotSameAs(variants);
        assertThat(result.state()).isEqualTo(ExperimentState.RUNNING);
        assertThat(result.version()).isEqualTo(5L);
    }

    @Test
    void withVariants_shouldReturnEmptyVariantsWhenInputVariantsNull() {
        Experiment experiment =
                new Experiment(UUID.randomUUID(), "flag-b", List.of(), ExperimentState.DRAFT, 1L, null, null);

        Experiment result = experimentAggregateMapper.withVariants(experiment, null);

        assertThat(result.variants()).isEmpty();
    }

    @Test
    void withVariants_shouldReturnEmptyVariantsWhenInputVariantsEmpty() {
        Experiment experiment =
                new Experiment(UUID.randomUUID(), "flag-c", List.of(), ExperimentState.APPROVED, 2L, null, null);

        Experiment result = experimentAggregateMapper.withVariants(experiment, List.of());

        assertThat(result.variants()).isEmpty();
    }
}
