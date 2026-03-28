package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.service.allocation.VariantBucketAllocator;
import io.github.rehody.abplatform.service.allocation.VariantBucketAllocator.BucketAllocation;
import io.github.rehody.abplatform.service.bucket.UserExperimentBucketResolver;
import io.github.rehody.abplatform.service.validation.ExperimentVariantAssignmentValidator;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentVariantResolver {

    private final ExperimentVariantAssignmentValidator experimentVariantAssignmentValidator;
    private final VariantBucketAllocator variantBucketAllocator;
    private final UserExperimentBucketResolver userExperimentBucketResolver;

    public Optional<ExperimentVariant> resolve(Experiment experiment, UUID userId) {
        List<ExperimentVariant> variants = experiment.variants();
        if (variants.isEmpty()) {
            return Optional.empty();
        }

        experimentVariantAssignmentValidator.validate(experiment.id(), variants);

        List<ExperimentVariant> orderedVariants = sortVariants(variants);
        int bucket = userExperimentBucketResolver.resolve(experiment.id(), userId);
        List<BucketAllocation> allocations = variantBucketAllocator.allocate(experiment.id(), orderedVariants);

        return Optional.of(selectVariantByBucket(bucket, allocations, orderedVariants));
    }

    private List<ExperimentVariant> sortVariants(List<ExperimentVariant> variants) {
        return variants.stream()
                .sorted(Comparator.comparingInt(ExperimentVariant::position))
                .toList();
    }

    private ExperimentVariant selectVariantByBucket(
            int bucket, List<BucketAllocation> allocations, List<ExperimentVariant> orderedVariants) {

        int intervalStart = 0;

        for (BucketAllocation allocation : allocations) {
            int intervalEnd = intervalStart + allocation.bucketCount();
            if (bucket < intervalEnd) {
                return allocation.variant();
            }
            intervalStart = intervalEnd;
        }

        return orderedVariants.getLast();
    }
}
