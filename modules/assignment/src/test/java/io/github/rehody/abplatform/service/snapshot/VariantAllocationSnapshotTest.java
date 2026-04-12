package io.github.rehody.abplatform.service.snapshot;

import static io.github.rehody.abplatform.support.AssignmentFixtures.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VariantAllocationSnapshotTest {

    @Test
    void selectVariant_shouldReturnVariantForMatchingBucketRange() {
        ExperimentVariant control = variant(0, "control", "blue", 1);
        ExperimentVariant treatment = variant(1, "treatment", "red", 1);
        VariantAllocationSnapshot snapshot = new VariantAllocationSnapshot(
                List.of(new BucketRange(0, 4000, control), new BucketRange(4000, 10000, treatment)));

        ExperimentVariant resolved = snapshot.selectVariant(UUID.randomUUID(), 9999);

        assertThat(resolved).isEqualTo(treatment);
    }

    @Test
    void constructor_shouldDefensivelyCopyBucketRanges() {
        ExperimentVariant control = variant(0, "control", "blue", 1);
        List<BucketRange> ranges = new ArrayList<>();
        ranges.add(new BucketRange(0, 10000, control));

        VariantAllocationSnapshot snapshot = new VariantAllocationSnapshot(ranges);
        ranges.clear();

        assertThat(snapshot.selectVariant(UUID.randomUUID(), 42)).isEqualTo(control);
    }

    @Test
    void selectVariant_shouldThrowWhenBucketIsNotCovered() {
        VariantAllocationSnapshot snapshot =
                new VariantAllocationSnapshot(List.of(new BucketRange(0, 100, variant(0, "control", "blue", 1))));
        UUID experimentId = UUID.randomUUID();

        assertThatThrownBy(() -> snapshot.selectVariant(experimentId, 100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Assignment bucket 100 is not covered by variant ranges for experiment %s"
                        .formatted(experimentId));
    }
}
