package io.github.rehody.abplatform.service;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExperimentVariantResolver {

    private static final int BUCKET_POOL_SIZE = 10_000;
    private static final HashFunction VARIANT_HASH_FUNCTION = Hashing.murmur3_32_fixed();

    public Optional<ExperimentVariant> resolve(Experiment experiment, UUID userId) {
        List<ExperimentVariant> variants = getOrderedVariants(experiment);
        if (variants.isEmpty()) {
            return Optional.empty();
        }

        int bucket = resolveBucket(experiment.id(), userId);
        int bucketSize = BUCKET_POOL_SIZE / variants.size();
        int remainder = BUCKET_POOL_SIZE % variants.size();
        int intervalStart = 0;

        for (int index = 0; index < variants.size(); index++) {
            int intervalSize = bucketSize + (index < remainder ? 1 : 0);
            int intervalEnd = intervalStart + intervalSize;
            if (bucket < intervalEnd) {
                return Optional.of(variants.get(index));
            }
            intervalStart = intervalEnd;
        }

        return Optional.of(variants.getLast());
    }

    int resolveBucket(UUID experimentId, UUID userId) {
        validateInputs(experimentId, userId);

        String hashInput = userId + ":" + experimentId;
        int hash = VARIANT_HASH_FUNCTION
                .hashString(hashInput, StandardCharsets.UTF_8)
                .asInt();
        return Integer.remainderUnsigned(hash, BUCKET_POOL_SIZE);
    }

    private void validateInputs(UUID experimentId, UUID userId) {
        Objects.requireNonNull(experimentId, "experimentId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
    }

    private List<ExperimentVariant> getOrderedVariants(Experiment experiment) {
        List<ExperimentVariant> variants = experiment.variants();
        validateUniquePositions(experiment.id(), variants);
        return variants.stream()
                .sorted(Comparator.comparingInt(ExperimentVariant::position))
                .toList();
    }

    private void validateUniquePositions(UUID experimentId, List<ExperimentVariant> variants) {
        Set<Integer> seenPositions = new HashSet<>();
        for (ExperimentVariant variant : variants) {
            int position = variant.position();
            boolean added = seenPositions.add(position);
            if (!added) {
                throw new IllegalStateException(
                        "Duplicate variant position for experiment %s: %d".formatted(experimentId, position));
            }
        }
    }
}
