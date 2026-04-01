package io.github.rehody.abplatform.service.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BucketAllocationTest {

    @Test
    void withAdditionalBucket_shouldIncrementBucketCountAndPreserveOtherFields() {
        var variant = io.github.rehody.abplatform.support.AssignmentFixtures.variant(3, "treatment", "red", 5);
        BucketAllocation allocation = new BucketAllocation(3, variant, 41, BigDecimal.valueOf(7));

        BucketAllocation updated = allocation.withAdditionalBucket();

        assertThat(updated.position()).isEqualTo(3);
        assertThat(updated.variant()).isEqualTo(variant);
        assertThat(updated.bucketCount()).isEqualTo(42);
        assertThat(updated.remainder()).isEqualTo(BigDecimal.valueOf(7));
    }
}
