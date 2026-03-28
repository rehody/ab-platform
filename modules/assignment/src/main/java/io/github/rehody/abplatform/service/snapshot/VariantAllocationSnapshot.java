package io.github.rehody.abplatform.service.snapshot;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.List;
import java.util.UUID;

public record VariantAllocationSnapshot(List<BucketRange> bucketRanges) {
    public VariantAllocationSnapshot {
        bucketRanges = List.copyOf(bucketRanges);
    }

    public ExperimentVariant selectVariant(UUID experimentId, int bucket) {
        for (BucketRange bucketRange : bucketRanges) {
            if (bucketRange.contains(bucket)) {
                return bucketRange.variant();
            }
        }

        throw new IllegalStateException("Assignment bucket %d is not covered by variant ranges for experiment %s"
                .formatted(bucket, experimentId));
    }
}
