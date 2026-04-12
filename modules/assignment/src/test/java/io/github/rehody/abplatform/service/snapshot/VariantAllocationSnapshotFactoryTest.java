package io.github.rehody.abplatform.service.snapshot;

import static io.github.rehody.abplatform.support.AssignmentFixtures.runningExperiment;
import static io.github.rehody.abplatform.support.AssignmentFixtures.stringValue;
import static io.github.rehody.abplatform.support.AssignmentFixtures.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
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
    private VariantBucketAllocator variantBucketAllocator;

    private VariantAllocationSnapshotFactory variantAllocationSnapshotFactory;

    @BeforeEach
    void setUp() {
        variantAllocationSnapshotFactory = new VariantAllocationSnapshotFactory(variantBucketAllocator);
    }

    @Test
    void create_shouldBuildBucketRangesFromAllocationsAndPreserveSortedVariantMapping() {
        ExperimentVariant control = variant(0, "control", "blue", 1);
        ExperimentVariant treatment = variant(1, "treatment", "red", 1);
        Experiment experiment = runningExperiment("flag-a", "CHECKOUT", List.of(treatment, control), 2L);
        when(variantBucketAllocator.allocate(experiment.id(), List.of(treatment), 500))
                .thenReturn(List.of(new BucketAllocation(treatment.position(), treatment, 500, BigDecimal.ZERO)));

        VariantAllocationSnapshot snapshot = variantAllocationSnapshotFactory.create(experiment);

        assertThat(snapshot.bucketRanges())
                .containsExactly(new BucketRange(0, 9500, control), new BucketRange(9500, 10000, treatment));
    }

    @Test
    void create_shouldRejectVariantWithNullWeight() {
        ExperimentVariant control = variant(0, "control", "blue", 1);
        ExperimentVariant invalid = new ExperimentVariant(
                UUID.randomUUID(), "broken", stringValue("red"), 1, null, ExperimentVariantType.REGULAR);
        Experiment experiment = runningExperiment("flag-b", "CHECKOUT", List.of(control, invalid), 1L);
        VariantAllocationSnapshotFactory snapshotFactory =
                new VariantAllocationSnapshotFactory(new VariantBucketAllocator());

        assertThatThrownBy(() -> snapshotFactory.create(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid REGULAR weight for experiment %s, variant %s: null"
                        .formatted(experiment.id(), invalid.id()));
    }

    @Test
    void create_shouldRejectVariantWithNonPositiveWeight() {
        ExperimentVariant control = variant(0, "control", "blue", 1);
        ExperimentVariant invalid = variant(0, "broken", "red", BigDecimal.ZERO);
        Experiment experiment = runningExperiment("flag-c", "CHECKOUT", List.of(control, invalid), 1L);
        VariantAllocationSnapshotFactory snapshotFactory =
                new VariantAllocationSnapshotFactory(new VariantBucketAllocator());

        assertThatThrownBy(() -> snapshotFactory.create(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid REGULAR weight for experiment %s, variant %s: 0"
                        .formatted(experiment.id(), invalid.id()));
    }

    @Test
    void create_shouldRejectExperimentWithoutRegularVariants() {
        Experiment experiment = runningExperiment("flag-d", "CHECKOUT", List.of(variant(0, "control", "blue", 1)), 3L);

        assertThatThrownBy(() -> variantAllocationSnapshotFactory.create(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Running experiment %s must contain at least one REGULAR variant".formatted(experiment.id()));

        verify(variantBucketAllocator, never()).allocate(any(), any(), anyInt());
    }

    @Test
    void create_shouldRejectExperimentWithoutSingleControlVariant() {
        Experiment experiment = runningExperiment(
                "flag-control",
                "CHECKOUT",
                List.of(variant(0, "control-a", "blue", 1), variant(1, "control-b", "green", 1)),
                3L);

        assertThatThrownBy(() -> variantAllocationSnapshotFactory.create(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Running experiment %s must contain exactly one CONTROL variant".formatted(experiment.id()));

        verify(variantBucketAllocator, never()).allocate(any(), any(), anyInt());
    }

    @Test
    void create_shouldRejectNonPositiveRegularBucketPool() {
        ExperimentVariant control = variant(0, "control", "blue", 1);
        ExperimentVariant treatment = variant(1, "treatment", "red", 1);
        ExperimentRolloutPlan rolloutPlan = org.mockito.Mockito.mock(ExperimentRolloutPlan.class);
        Experiment experiment = org.mockito.Mockito.mock(Experiment.class);

        when(experiment.id()).thenReturn(UUID.randomUUID());
        when(experiment.variants()).thenReturn(List.of(control, treatment));
        when(experiment.rolloutPlan()).thenReturn(rolloutPlan);
        when(rolloutPlan.regularBucketPoolSize(anyInt())).thenReturn(0);
        when(rolloutPlan.controlBucketPoolSize(anyInt())).thenReturn(10_000);

        assertThatThrownBy(() -> variantAllocationSnapshotFactory.create(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Regular bucket pool must be positive for experiment %s".formatted(experiment.id()));

        verify(variantBucketAllocator, never()).allocate(any(), any(), anyInt());
    }

    @Test
    void create_shouldThrowWhenAllocationsDoNotCoverFullBucketPool() {
        ExperimentVariant control = variant(0, "control", "blue", 1);
        ExperimentVariant treatment = variant(1, "treatment", "red", 1);
        Experiment experiment = runningExperiment("flag-e", "CHECKOUT", List.of(control, treatment), 4L);
        when(variantBucketAllocator.allocate(experiment.id(), List.of(treatment), 500))
                .thenReturn(List.of(new BucketAllocation(treatment.position(), treatment, 499, BigDecimal.ZERO)));

        assertThatThrownBy(() -> variantAllocationSnapshotFactory.create(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bucket ranges do not cover pool size for experiment %s. Covered: 9999"
                        .formatted(experiment.id()));
    }

    @Test
    void create_shouldThrowWhenAllocatorReturnsNoRanges() {
        ExperimentVariant control = variant(0, "control", "blue", 1);
        ExperimentVariant treatment = variant(1, "treatment", "red", 1);
        Experiment experiment = new Experiment(
                experimentId("flag-f"),
                "flag-f",
                "CHECKOUT",
                ExperimentRolloutPlan.of(100, false, false),
                List.of(control, treatment),
                ExperimentState.RUNNING,
                5L,
                null,
                null);
        when(variantBucketAllocator.allocate(experiment.id(), List.of(treatment), 10000))
                .thenReturn(List.of());

        assertThatThrownBy(() -> variantAllocationSnapshotFactory.create(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bucket ranges do not cover pool size for experiment %s. Covered: 0"
                        .formatted(experiment.id()));
    }

    private UUID experimentId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
