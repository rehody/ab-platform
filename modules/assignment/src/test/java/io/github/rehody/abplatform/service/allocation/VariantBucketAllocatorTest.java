package io.github.rehody.abplatform.service.allocation;

import static io.github.rehody.abplatform.service.VariantBucketPolicy.BUCKET_POOL_SIZE;
import static io.github.rehody.abplatform.support.AssignmentFixtures.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class VariantBucketAllocatorTest {

    private final VariantBucketAllocator variantBucketAllocator = new VariantBucketAllocator();

    @Test
    void allocate_shouldDistributeBucketsByWeightAndCoverEntirePool() {
        List<ExperimentVariant> variants = List.of(
                variant(0, "control", "blue", 1),
                variant(1, "variant-b", "green", 2),
                variant(2, "variant-c", "red", 3));

        List<BucketAllocation> allocations = variantBucketAllocator.allocate(UUID.randomUUID(), variants);

        assertThat(allocations).extracting(BucketAllocation::bucketCount).containsExactly(1667, 3333, 5000);
        assertThat(allocations.stream().mapToInt(BucketAllocation::bucketCount).sum())
                .isEqualTo(BUCKET_POOL_SIZE);
        assertThat(allocations).allMatch(allocation -> allocation.bucketCount() >= 1);
    }

    @Test
    void allocate_shouldUseLowerPositionAsTieBreakWhenRemaindersAreEqual() {
        List<ExperimentVariant> variants =
                List.of(variant(0, "a", "A", 1), variant(1, "b", "B", 1), variant(2, "c", "C", 1));

        List<BucketAllocation> allocations = variantBucketAllocator.allocate(UUID.randomUUID(), variants);

        assertThat(allocations).extracting(BucketAllocation::bucketCount).containsExactly(3334, 3333, 3333);
    }

    @Test
    void allocate_shouldAssignExactlyOneBucketToEachVariantWhenPoolSizeMatchesVariantCount() {
        List<ExperimentVariant> variants = IntStream.range(0, BUCKET_POOL_SIZE)
                .mapToObj(index -> variant(index, "v-" + index, "value-" + index, 1))
                .toList();

        List<BucketAllocation> allocations = variantBucketAllocator.allocate(UUID.randomUUID(), variants);

        assertThat(allocations).hasSize(BUCKET_POOL_SIZE);
        assertThat(allocations).allMatch(allocation -> allocation.bucketCount() == 1);
    }

    @Test
    void allocate_shouldReturnInitialAllocationsWhenWeightedSplitIsAlreadyExact() {
        List<ExperimentVariant> variants = List.of(variant(0, "control", "blue", 1), variant(1, "treatment", "red", 1));

        List<BucketAllocation> allocations = variantBucketAllocator.allocate(UUID.randomUUID(), variants);

        assertThat(allocations).extracting(BucketAllocation::bucketCount).containsExactly(5000, 5000);
    }

    @Test
    void allocate_shouldThrowWhenVariantCountExceedsBucketPool() {
        List<ExperimentVariant> variants = IntStream.range(0, BUCKET_POOL_SIZE + 1)
                .mapToObj(index -> variant(index, "v-" + index, "value-" + index, 1))
                .toList();
        UUID experimentId = UUID.randomUUID();

        assertThatThrownBy(() -> variantBucketAllocator.allocate(experimentId, variants))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Experiment %s has %d variants, which exceeds bucket pool size %d"
                        .formatted(experimentId, BUCKET_POOL_SIZE + 1, BUCKET_POOL_SIZE));
    }
}
