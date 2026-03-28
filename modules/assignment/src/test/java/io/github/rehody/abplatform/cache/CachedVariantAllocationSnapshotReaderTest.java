package io.github.rehody.abplatform.cache;

import static io.github.rehody.abplatform.support.AssignmentFixtures.runningExperiment;
import static io.github.rehody.abplatform.support.AssignmentFixtures.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.service.snapshot.BucketRange;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshot;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshotFactory;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CachedVariantAllocationSnapshotReaderTest {

    @Mock
    private AssignmentPlanCache assignmentPlanCache;

    @Mock
    private VariantAllocationSnapshotFactory variantAllocationSnapshotFactory;

    private CachedVariantAllocationSnapshotReader cachedVariantAllocationSnapshotReader;

    @BeforeEach
    void setUp() {
        cachedVariantAllocationSnapshotReader =
                new CachedVariantAllocationSnapshotReader(assignmentPlanCache, variantAllocationSnapshotFactory);
    }

    @Test
    void get_shouldReturnCachedSnapshotWithoutCallingFactoryWhenCacheHits() {
        Experiment experiment = runningExperiment("flag-a", List.of(variant(0, "control", "blue", 1)), 7L);
        VariantAllocationSnapshot snapshot =
                new VariantAllocationSnapshot(List.of(new BucketRange(0, 10000, variant(0, "control", "blue", 1))));
        when(assignmentPlanCache.getOrLoad(eq(experiment.id() + ":7"), any())).thenReturn(Optional.of(snapshot));

        VariantAllocationSnapshot result = cachedVariantAllocationSnapshotReader.get(experiment);

        assertThat(result).isEqualTo(snapshot);
        verify(assignmentPlanCache).getOrLoad(eq(experiment.id() + ":7"), any());
        verify(variantAllocationSnapshotFactory, never()).create(any());
    }

    @Test
    void get_shouldBuildSnapshotViaFactoryWhenCacheMisses() {
        Experiment experiment = runningExperiment("flag-b", List.of(variant(0, "control", "blue", 1)), 3L);
        VariantAllocationSnapshot snapshot =
                new VariantAllocationSnapshot(List.of(new BucketRange(0, 10000, variant(0, "control", "blue", 1))));
        when(assignmentPlanCache.getOrLoad(eq(experiment.id() + ":3"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Optional<VariantAllocationSnapshot>> loader = invocation.getArgument(1);
            return loader.get();
        });
        when(variantAllocationSnapshotFactory.create(experiment)).thenReturn(snapshot);

        VariantAllocationSnapshot result = cachedVariantAllocationSnapshotReader.get(experiment);

        assertThat(result).isEqualTo(snapshot);
        verify(variantAllocationSnapshotFactory).create(experiment);
    }

    @Test
    void get_shouldThrowWhenCacheReturnsEmptySnapshot() {
        Experiment experiment = runningExperiment("flag-c", List.of(variant(0, "control", "blue", 1)), 9L);
        when(assignmentPlanCache.getOrLoad(eq(experiment.id() + ":9"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cachedVariantAllocationSnapshotReader.get(experiment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Assignment plan snapshot not found for experiment %s".formatted(experiment.id()));
    }
}
