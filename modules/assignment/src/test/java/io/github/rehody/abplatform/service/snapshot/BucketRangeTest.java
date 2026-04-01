package io.github.rehody.abplatform.service.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BucketRangeTest {

    @Test
    void contains_shouldUseInclusiveLowerBoundAndExclusiveUpperBound() {
        var variant = io.github.rehody.abplatform.support.AssignmentFixtures.variant(0, "control", "blue", 1);
        BucketRange bucketRange = new BucketRange(10, 20, variant);

        assertThat(bucketRange.contains(10)).isTrue();
        assertThat(bucketRange.contains(19)).isTrue();
        assertThat(bucketRange.contains(9)).isFalse();
        assertThat(bucketRange.contains(20)).isFalse();
    }

    @Test
    void constructor_shouldRejectNullVariant() {
        assertThatThrownBy(() -> new BucketRange(0, 1, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("variant must not be null");
    }
}
