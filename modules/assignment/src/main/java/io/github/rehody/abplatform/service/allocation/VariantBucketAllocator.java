package io.github.rehody.abplatform.service.allocation;

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

    public List<BucketAllocation> allocate(UUID experimentId, List<ExperimentVariant> variants, int bucketPoolSize) {
        if (bucketPoolSize == 0 || variants.isEmpty()) {
            return List.of();
        }

        int remainingBucketPool = bucketPoolSize - variants.size();
        validateRemainingBucketPool(experimentId, variants.size(), bucketPoolSize, remainingBucketPool);

        if (remainingBucketPool == 0) {
            return allocateGuaranteedBuckets(variants);
        }

        BigDecimal totalWeight = calculateTotalWeight(experimentId, variants);
        List<BucketAllocation> initialAllocations =
                allocateWeightedBuckets(experimentId, variants, totalWeight, remainingBucketPool);

        int allocatedBucketCount = initialAllocations.stream()
                .mapToInt(BucketAllocation::bucketCount)
                .sum();

        int remainingBuckets = bucketPoolSize - allocatedBucketCount;
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

    private BigDecimal calculateTotalWeight(UUID experimentId, List<ExperimentVariant> variants) {
        return variants.stream()
                .map(variant -> weightOf(experimentId, variant))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<BucketAllocation> allocateWeightedBuckets(
            UUID experimentId, List<ExperimentVariant> variants, BigDecimal totalWeight, int remainingBucketPool) {
        return variants.stream()
                .map(variant -> createWeightedAllocation(experimentId, variant, totalWeight, remainingBucketPool))
                .toList();
    }

    private BucketAllocation createWeightedAllocation(
            UUID experimentId, ExperimentVariant variant, BigDecimal totalWeight, int remainingBucketPool) {
        BigDecimal weight = weightOf(experimentId, variant);
        BigDecimal scaledWeight = weight.multiply(BigDecimal.valueOf(remainingBucketPool));
        BigDecimal additionalBuckets = scaledWeight.divideToIntegralValue(totalWeight);
        BigDecimal remainder = scaledWeight.remainder(totalWeight);

        return new BucketAllocation(variant.position(), variant, 1 + additionalBuckets.intValueExact(), remainder);
    }

    private BigDecimal weightOf(UUID experimentId, ExperimentVariant variant) {
        BigDecimal weight = variant.weight();
        if (weight == null || weight.signum() <= 0) {
            throw new IllegalStateException("Invalid REGULAR weight for experiment %s, variant %s: %s"
                    .formatted(experimentId, variant.id(), weight));
        }
        return weight;
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
                .map(allocation -> addBucketIfNeeded(allocation, positionsReceivingExtraBucket))
                .toList();
    }

    private BucketAllocation addBucketIfNeeded(
            BucketAllocation allocation, Set<Integer> positionsReceivingExtraBucket) {
        if (positionsReceivingExtraBucket.contains(allocation.position())) {
            return allocation.withExtraBucket();
        }
        return allocation;
    }

    private void validateRemainingBucketPool(
            UUID experimentId, int variantCount, int bucketPoolSize, int remainingBucketPool) {
        if (remainingBucketPool < 0) {
            throw new IllegalStateException("Experiment %s has %d variants, which exceeds bucket pool size %d"
                    .formatted(experimentId, variantCount, bucketPoolSize));
        }
    }
}
