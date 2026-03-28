package io.github.rehody.abplatform.service.allocation;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.math.BigDecimal;

public record BucketAllocation(int position, ExperimentVariant variant, int bucketCount, BigDecimal remainder) {
    public BucketAllocation withAdditionalBucket() {
        return new BucketAllocation(position, variant, bucketCount + 1, remainder);
    }
}
