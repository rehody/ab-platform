package io.github.rehody.abplatform.service.snapshot;

import static io.github.rehody.abplatform.service.VariantBucketPolicy.BUCKET_POOL_SIZE;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.service.allocation.BucketAllocation;
import io.github.rehody.abplatform.service.allocation.VariantBucketAllocator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VariantAllocationSnapshotFactory {

    private final AssignmentVariantsPreparer assignmentVariantsPreparer;
    private final VariantBucketAllocator variantBucketAllocator;

    public VariantAllocationSnapshot create(Experiment experiment) {
        List<ExperimentVariant> orderedVariants = assignmentVariantsPreparer.prepare(experiment);
        validateAllocationInputs(experiment.id(), orderedVariants);
        List<BucketAllocation> allocations = variantBucketAllocator.allocate(experiment.id(), orderedVariants);
        List<BucketRange> bucketRanges = createBucketRanges(allocations);

        validateFullBucketCoverage(experiment.id(), bucketRanges);

        return new VariantAllocationSnapshot(bucketRanges);
    }

    private List<BucketRange> createBucketRanges(List<BucketAllocation> allocations) {
        int rangeStart = 0;
        List<BucketRange> bucketRanges = new ArrayList<>(allocations.size());

        for (BucketAllocation allocation : allocations) {
            int rangeEnd = rangeStart + allocation.bucketCount();
            bucketRanges.add(new BucketRange(rangeStart, rangeEnd, allocation.variant()));
            rangeStart = rangeEnd;
        }

        return List.copyOf(bucketRanges);
    }

    private void validateAllocationInputs(UUID experimentId, List<ExperimentVariant> variants) {
        BigDecimal totalWeight = variants.stream()
                .map(variant -> validatePositiveWeight(experimentId, variant))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.signum() <= 0) {
            throw new IllegalStateException(
                    "Total assignment weight must be positive for experiment %s".formatted(experimentId));
        }
    }

    private BigDecimal validatePositiveWeight(UUID experimentId, ExperimentVariant variant) {
        BigDecimal weight = variant.weight();
        if (weight == null || weight.signum() <= 0) {
            throw new IllegalStateException("Invalid assignment weight for experiment %s, variant %s: %s"
                    .formatted(experimentId, variant.id(), weight));
        }
        return weight;
    }

    private void validateFullBucketCoverage(UUID experimentId, List<BucketRange> bucketRanges) {
        int coveredBuckets = 0;
        if (!bucketRanges.isEmpty()) {
            coveredBuckets = bucketRanges.getLast().endExclusive();
        }

        if (coveredBuckets != BUCKET_POOL_SIZE) {
            throw new IllegalStateException("Bucket ranges do not cover pool size for experiment %s. Covered: %d"
                    .formatted(experimentId, coveredBuckets));
        }
    }
}
