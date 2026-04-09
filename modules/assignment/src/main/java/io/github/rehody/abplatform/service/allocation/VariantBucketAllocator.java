package io.github.rehody.abplatform.service.allocation;

import static io.github.rehody.abplatform.service.VariantBucketPolicy.BUCKET_POOL_SIZE;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class VariantBucketAllocator {

    public List<BucketAllocation> allocate(
            UUID experimentId, List<ExperimentVariant> variants, Map<UUID, BigDecimal> assignmentWeights) {

        List<ExperimentVariant> participatingVariants =
                filterParticipatingVariants(experimentId, variants, assignmentWeights);

        int remainingBucketPool = BUCKET_POOL_SIZE - participatingVariants.size();
        validateRemainingBucketPool(experimentId, participatingVariants.size(), remainingBucketPool);

        if (remainingBucketPool == 0) {
            return allocateGuaranteedBuckets(participatingVariants);
        }

        BigDecimal totalWeight = calculateTotalWeight(experimentId, participatingVariants, assignmentWeights);
        List<BucketAllocation> initialAllocations = allocateWeightedBuckets(
                experimentId, participatingVariants, assignmentWeights, totalWeight, remainingBucketPool);

        int allocatedBucketCount = initialAllocations.stream()
                .mapToInt(BucketAllocation::bucketCount)
                .sum();

        int remainingBuckets = BUCKET_POOL_SIZE - allocatedBucketCount;
        if (remainingBuckets == 0) {
            return initialAllocations;
        }

        return distributeRemainingBuckets(initialAllocations, remainingBuckets);
    }

    private List<ExperimentVariant> filterParticipatingVariants(
            UUID experimentId, List<ExperimentVariant> variants, Map<UUID, BigDecimal> assignmentWeights) {
        return variants.stream()
                .filter(variant -> assignmentWeightOf(experimentId, variant, assignmentWeights)
                                .signum()
                        > 0)
                .toList();
    }

    private List<BucketAllocation> allocateGuaranteedBuckets(List<ExperimentVariant> variants) {
        return variants.stream()
                .map(variant -> new BucketAllocation(variant.position(), variant, 1, BigDecimal.ZERO))
                .toList();
    }

    private BigDecimal calculateTotalWeight(
            UUID experimentId, List<ExperimentVariant> variants, Map<UUID, BigDecimal> assignmentWeights) {
        return variants.stream()
                .map(variant -> assignmentWeightOf(experimentId, variant, assignmentWeights))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<BucketAllocation> allocateWeightedBuckets(
            UUID experimentId,
            List<ExperimentVariant> variants,
            Map<UUID, BigDecimal> assignmentWeights,
            BigDecimal totalWeight,
            int remainingBucketPool) {
        return variants.stream()
                .map(variant -> createWeightedAllocation(
                        experimentId, variant, assignmentWeights, totalWeight, remainingBucketPool))
                .toList();
    }

    private BucketAllocation createWeightedAllocation(
            UUID experimentId,
            ExperimentVariant variant,
            Map<UUID, BigDecimal> assignmentWeights,
            BigDecimal totalWeight,
            int remainingBucketPool) {
        BigDecimal assignmentWeight = assignmentWeightOf(experimentId, variant, assignmentWeights);
        BigDecimal scaledWeight = assignmentWeight.multiply(BigDecimal.valueOf(remainingBucketPool));
        BigDecimal additionalBuckets = scaledWeight.divideToIntegralValue(totalWeight);
        BigDecimal remainder = scaledWeight.remainder(totalWeight);

        return new BucketAllocation(variant.position(), variant, 1 + additionalBuckets.intValueExact(), remainder);
    }

    private BigDecimal assignmentWeightOf(
            UUID experimentId, ExperimentVariant variant, Map<UUID, BigDecimal> assignmentWeights) {
        BigDecimal assignmentWeight = assignmentWeights.get(variant.id());
        if (assignmentWeight == null) {
            throw new IllegalStateException(
                    "Missing assignment weight for experiment %s, variant %s".formatted(experimentId, variant.id()));
        }
        return assignmentWeight;
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

    private void validateRemainingBucketPool(UUID experimentId, int variantCount, int remainingBucketPool) {
        if (remainingBucketPool < 0) {
            throw new IllegalStateException("Experiment %s has %d variants, which exceeds bucket pool size %d"
                    .formatted(experimentId, variantCount, BUCKET_POOL_SIZE));
        }
    }
}
