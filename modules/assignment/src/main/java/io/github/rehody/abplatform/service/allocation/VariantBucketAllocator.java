package io.github.rehody.abplatform.service.allocation;

import static io.github.rehody.abplatform.service.VariantBucketPolicy.BUCKET_POOL_SIZE;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class VariantBucketAllocator {

    public List<BucketAllocation> allocate(UUID experimentId, List<ExperimentVariant> variants) {
        int remainingBucketPool = BUCKET_POOL_SIZE - variants.size();
        validateRemainingBucketPool(experimentId, variants.size(), remainingBucketPool);

        if (remainingBucketPool == 0) {
            return allocateGuaranteedBuckets(variants);
        }

        BigDecimal totalWeight = calculateTotalWeight(variants);
        List<BucketAllocation> initialAllocations = allocateWeightedBuckets(variants, totalWeight, remainingBucketPool);

        int allocatedBucketCount = initialAllocations.stream()
                .mapToInt(BucketAllocation::bucketCount)
                .sum();

        validateAllocatedBucketCount(experimentId, allocatedBucketCount);

        int remainingBuckets = BUCKET_POOL_SIZE - allocatedBucketCount;
        if (remainingBuckets == 0) {
            return initialAllocations;
        }

        return distributeRemainingBuckets(initialAllocations, remainingBuckets);
    }

    private List<BucketAllocation> allocateGuaranteedBuckets(List<ExperimentVariant> variants) {
        return variants.stream()
                .map(variant -> new BucketAllocation(variant.position(), variant, 1, BigDecimal.ZERO))
                .toList();
    }

    private BigDecimal calculateTotalWeight(List<ExperimentVariant> variants) {
        return variants.stream().map(ExperimentVariant::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<BucketAllocation> allocateWeightedBuckets(
            List<ExperimentVariant> variants, BigDecimal totalWeight, int remainingBucketPool) {
        return variants.stream()
                .map(variant -> createWeightedAllocation(variant, totalWeight, remainingBucketPool))
                .toList();
    }

    private BucketAllocation createWeightedAllocation(
            ExperimentVariant variant, BigDecimal totalWeight, int remainingBucketPool) {
        BigDecimal scaledWeight = variant.weight().multiply(BigDecimal.valueOf(remainingBucketPool));
        BigDecimal additionalBuckets = scaledWeight.divideToIntegralValue(totalWeight);
        BigDecimal remainder = scaledWeight.remainder(totalWeight);

        return new BucketAllocation(variant.position(), variant, 1 + additionalBuckets.intValueExact(), remainder);
    }

    private List<BucketAllocation> distributeRemainingBuckets(
            List<BucketAllocation> allocations, int remainingBuckets) {
        Set<Integer> positionsReceivingExtraBucket = allocations.stream()
                .sorted(Comparator.comparing(BucketAllocation::remainder, Comparator.reverseOrder())
                        .thenComparingInt(BucketAllocation::position))
                .limit(remainingBuckets)
                .map(BucketAllocation::position)
                .collect(Collectors.toSet());

        return allocations.stream()
                .map(allocation -> positionsReceivingExtraBucket.contains(allocation.position())
                        ? allocation.withAdditionalBucket()
                        : allocation)
                .toList();
    }

    private void validateRemainingBucketPool(UUID experimentId, int variantCount, int remainingBucketPool) {
        if (remainingBucketPool < 0) {
            throw new IllegalStateException("Experiment %s has %d variants, which exceeds bucket pool size %d"
                    .formatted(experimentId, variantCount, BUCKET_POOL_SIZE));
        }
    }

    private void validateAllocatedBucketCount(UUID experimentId, int allocatedBucketCount) {
        if (allocatedBucketCount > BUCKET_POOL_SIZE) {
            throw new IllegalStateException(
                    "Allocated bucket count exceeded pool size for experiment %s".formatted(experimentId));
        }
    }
}
