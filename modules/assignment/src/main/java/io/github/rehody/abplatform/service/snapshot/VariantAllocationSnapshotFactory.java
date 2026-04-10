package io.github.rehody.abplatform.service.snapshot;

import static io.github.rehody.abplatform.service.VariantBucketPolicy.BUCKET_POOL_SIZE;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.service.allocation.BucketAllocation;
import io.github.rehody.abplatform.service.allocation.VariantBucketAllocator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VariantAllocationSnapshotFactory {

    private final VariantBucketAllocator variantBucketAllocator;

    public VariantAllocationSnapshot create(Experiment experiment) {
        List<ExperimentVariant> variants = sortVariants(experiment.variants());
        ExperimentVariant controlVariant = findControlVariant(experiment.id(), variants);
        List<ExperimentVariant> regularVariants = selectRegularVariants(variants);

        int regularBucketPool = experiment.rolloutPlan().regularBucketPoolSize(BUCKET_POOL_SIZE);
        int controlBucketPool = experiment.rolloutPlan().controlBucketPoolSize(BUCKET_POOL_SIZE);

        validateRegularBucketPool(experiment.id(), regularVariants, regularBucketPool);

        List<BucketAllocation> allocations = buildAllocations(
                experiment.id(), controlVariant, controlBucketPool, regularVariants, regularBucketPool);

        List<BucketRange> bucketRanges = createBucketRanges(allocations);
        validateFullBucketCoverage(experiment.id(), bucketRanges);
        return new VariantAllocationSnapshot(bucketRanges);
    }

    private List<BucketAllocation> buildAllocations(
            UUID experimentId,
            ExperimentVariant controlVariant,
            int controlBucketPool,
            List<ExperimentVariant> regularVariants,
            int regularBucketPool) {

        List<BucketAllocation> allocations = new ArrayList<>();

        if (controlBucketPool > 0) {
            allocations.add(new BucketAllocation(
                    controlVariant.position(), controlVariant, controlBucketPool, BigDecimal.ZERO));
        }

        List<BucketAllocation> allocated =
                variantBucketAllocator.allocate(experimentId, regularVariants, regularBucketPool);
        allocations.addAll(allocated);

        return allocations.stream()
                .sorted(Comparator.comparingInt(BucketAllocation::position))
                .toList();
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

    private List<ExperimentVariant> sortVariants(List<ExperimentVariant> variants) {
        return variants.stream()
                .sorted(Comparator.comparingInt(ExperimentVariant::position))
                .toList();
    }

    private ExperimentVariant findControlVariant(UUID experimentId, List<ExperimentVariant> variants) {
        List<ExperimentVariant> controlVariants =
                variants.stream().filter(ExperimentVariant::isControl).toList();

        if (controlVariants.size() != 1) {
            throw new IllegalStateException(
                    "Running experiment %s must contain exactly one CONTROL variant".formatted(experimentId));
        }

        return controlVariants.getFirst();
    }

    private List<ExperimentVariant> selectRegularVariants(List<ExperimentVariant> variants) {
        return variants.stream().filter(ExperimentVariant::isRegular).toList();
    }

    private void validateRegularBucketPool(
            UUID experimentId, List<ExperimentVariant> regularVariants, int regularBucketPool) {
        if (regularVariants.isEmpty()) {
            throw new IllegalStateException(
                    "Running experiment %s must contain at least one REGULAR variant".formatted(experimentId));
        }

        if (regularBucketPool <= 0) {
            throw new IllegalStateException(
                    "Regular bucket pool must be positive for experiment %s".formatted(experimentId));
        }
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
