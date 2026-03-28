package io.github.rehody.abplatform.service.validation;

import static io.github.rehody.abplatform.service.VariantBucketPolicy.*;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExperimentVariantAssignmentValidator {

    public void validate(UUID experimentId, List<ExperimentVariant> variants) {
        validateVariantCount(experimentId, variants.size());
        validateUniquePositions(experimentId, variants);
        validatePositiveWeights(experimentId, variants);
    }

    private void validateVariantCount(UUID experimentId, int variantCount) {
        if (variantCount > BUCKET_POOL_SIZE) {
            throw new IllegalStateException("Experiment %s has %d variants, which exceeds bucket pool size %d"
                    .formatted(experimentId, variantCount, BUCKET_POOL_SIZE));
        }
    }

    private void validateUniquePositions(UUID experimentId, List<ExperimentVariant> variants) {
        Set<Integer> seenPositions = new HashSet<>();

        for (ExperimentVariant variant : variants) {
            int position = variant.position();
            boolean unique = seenPositions.add(position);

            if (!unique) {
                throw new IllegalStateException(
                        "Duplicate variant position for experiment %s: %d".formatted(experimentId, position));
            }
        }
    }

    private void validatePositiveWeights(UUID experimentId, List<ExperimentVariant> variants) {
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (ExperimentVariant variant : variants) {
            BigDecimal weight = variant.weight();

            if (weight == null || weight.signum() <= 0) {
                throw new IllegalStateException("Invalid variant weight for experiment %s, variant %s: %s"
                        .formatted(experimentId, variant.id(), weight));
            }

            totalWeight = totalWeight.add(weight);
        }

        if (totalWeight.signum() <= 0) {
            throw new IllegalStateException(
                    "Total variant weight must be positive for experiment %s".formatted(experimentId));
        }
    }
}
