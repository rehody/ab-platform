package io.github.rehody.abplatform.service.snapshot;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.Objects;

public record BucketRange(int startInclusive, int endExclusive, ExperimentVariant variant) {
    public BucketRange {
        Objects.requireNonNull(variant, "variant must not be null");
    }

    public boolean contains(int bucket) {
        return bucket >= startInclusive && bucket < endExclusive;
    }
}
