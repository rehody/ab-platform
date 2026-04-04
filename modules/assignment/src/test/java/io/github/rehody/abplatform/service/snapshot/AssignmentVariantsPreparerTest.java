package io.github.rehody.abplatform.service.snapshot;

import static io.github.rehody.abplatform.support.AssignmentFixtures.runningExperiment;
import static io.github.rehody.abplatform.support.AssignmentFixtures.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssignmentVariantsPreparerTest {

    private final AssignmentVariantsPreparer assignmentVariantsPreparer = new AssignmentVariantsPreparer();

    @Test
    void prepare_shouldReturnVariantsSortedByPosition() {
        ExperimentVariant last = variant(2, "last", "red", 1);
        ExperimentVariant first = variant(0, "first", "blue", 1);
        ExperimentVariant middle = variant(1, "middle", "green", 1);
        Experiment experiment = runningExperiment("flag-a", "CHECKOUT", List.of(last, first, middle), 1L);

        List<ExperimentVariant> prepared = assignmentVariantsPreparer.prepare(experiment);

        assertThat(prepared).containsExactly(first, middle, last);
    }

    @Test
    void prepare_shouldRejectExperimentWithoutVariants() {
        Experiment experiment = runningExperiment("flag-b", "CHECKOUT", List.of(), 2L);

        assertThatThrownBy(() -> assignmentVariantsPreparer.prepare(experiment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot prepare assignment variants for experiment %s without variants"
                        .formatted(experiment.id()));
    }
}
