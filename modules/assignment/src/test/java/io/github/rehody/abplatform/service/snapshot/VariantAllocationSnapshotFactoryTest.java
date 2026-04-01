package io.github.rehody.abplatform.service.snapshot;

import static io.github.rehody.abplatform.support.AssignmentFixtures.runningExperiment;
import static io.github.rehody.abplatform.support.AssignmentFixtures.stringValue;
import static io.github.rehody.abplatform.support.AssignmentFixtures.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.service.allocation.BucketAllocation;
import io.github.rehody.abplatform.service.allocation.VariantBucketAllocator;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VariantAllocationSnapshotFactoryTest {

    @Mock
    private AssignmentVariantsPreparer assignmentVariantsPreparer;

    @Mock
    private VariantBucketAllocator variantBucketAllocator;

    private VariantAllocationSnapshotFactory variantAllocationSnapshotFactory;

    @BeforeEach
    void setUp() {
        variantAllocationSnapshotFactory =
                new VariantAllocationSnapshotFactory(assignmentVariantsPreparer, variantBucketAllocator);
    }

    @Test
    void create_shouldBuildBucketRangesFromAllocationsAndPreserveSortedVariantMapping() {
        ExperimentVariant control = variant(0, "control", "blue", 1);
        ExperimentVariant treatment = variant(1, "treatment", "red", 1);
        Experiment experiment = runningExperiment("flag-a", List.of(treatment, control), 2L);
        when(assignmentVariantsPreparer.prepare(experiment)).thenReturn(List.of(control, treatment));
        when(variantBucketAllocator.allocate(experiment.id(), List.of(control, treatment)))
                .thenReturn(List.of(
                        new BucketAllocation(control.position(), control, 4000, BigDecimal.ZERO),
                        new BucketAllocation(treatment.position(), treatment, 6000, BigDecimal.ZERO)));

        VariantAllocationSnapshot snapshot = variantAllocationSnapshotFactory.create(experiment);

        assertThat(snapshot.bucketRanges())
                .containsExactly(new BucketRange(0, 4000, control), new BucketRange(4000, 10000, treatment));
    }

    @Test
    void create_shouldRejectVariantWithNullWeight() {
        ExperimentVariant invalid = new ExperimentVariant(
                UUID.randomUUID(), "broken", stringValue("red"), 0, null, ExperimentVariantType.REGULAR);
        Experiment experiment = runningExperiment("flag-b", List.of(invalid), 1L);
        when(assignmentVariantsPreparer.prepare(experiment)).thenReturn(List.of(invalid));

        assertThatThrownBy(() -> variantAllocationSnapshotFactory.create(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid assignment weight for experiment %s, variant %s: null"
                        .formatted(experiment.id(), invalid.id()));

        verify(variantBucketAllocator, never()).allocate(any(), any());
    }

    @Test
    void create_shouldRejectVariantWithNonPositiveWeight() {
        ExperimentVariant invalid = variant(0, "broken", "red", BigDecimal.ZERO);
        Experiment experiment = runningExperiment("flag-c", List.of(invalid), 1L);
        when(assignmentVariantsPreparer.prepare(experiment)).thenReturn(List.of(invalid));

        assertThatThrownBy(() -> variantAllocationSnapshotFactory.create(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid assignment weight for experiment %s, variant %s: 0"
                        .formatted(experiment.id(), invalid.id()));

        verify(variantBucketAllocator, never()).allocate(any(), any());
    }

    @Test
    void create_shouldRejectPreparedVariantsWhenTotalWeightIsNotPositive() {
        Experiment experiment = runningExperiment("flag-d", List.of(variant(0, "control", "blue", 1)), 3L);
        when(assignmentVariantsPreparer.prepare(experiment)).thenReturn(List.of());

        assertThatThrownBy(() -> variantAllocationSnapshotFactory.create(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Total assignment weight must be positive for experiment %s".formatted(experiment.id()));

        verify(variantBucketAllocator, never()).allocate(any(), any());
    }

    @Test
    void create_shouldThrowWhenAllocationsDoNotCoverFullBucketPool() {
        ExperimentVariant control = variant(0, "control", "blue", 1);
        ExperimentVariant treatment = variant(1, "treatment", "red", 1);
        Experiment experiment = runningExperiment("flag-e", List.of(control, treatment), 4L);
        when(assignmentVariantsPreparer.prepare(experiment)).thenReturn(List.of(control, treatment));
        when(variantBucketAllocator.allocate(experiment.id(), List.of(control, treatment)))
                .thenReturn(List.of(
                        new BucketAllocation(control.position(), control, 4000, BigDecimal.ZERO),
                        new BucketAllocation(treatment.position(), treatment, 5999, BigDecimal.ZERO)));

        assertThatThrownBy(() -> variantAllocationSnapshotFactory.create(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bucket ranges do not cover pool size for experiment %s. Covered: 9999"
                        .formatted(experiment.id()));
    }
}
