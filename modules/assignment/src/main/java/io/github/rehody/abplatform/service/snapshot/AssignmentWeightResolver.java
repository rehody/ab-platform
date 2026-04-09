package io.github.rehody.abplatform.service.snapshot;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AssignmentWeightResolver {

    private static final int WEIGHT_SCALE = 10;

    public Map<UUID, BigDecimal> resolve(Experiment experiment, List<ExperimentVariant> variants) {
        if (variants.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot resolve assignment weights for experiment %s without variants".formatted(experiment.id()));
        }

        BigDecimal totalRegularWeight = variants.stream()
                .filter(ExperimentVariant::isRegular)
                .map(ExperimentVariant::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        validateRegularWeights(experiment.id(), variants, totalRegularWeight);

        return variants.stream()
                .collect(Collectors.toMap(
                        ExperimentVariant::id,
                        variant -> experiment.rolloutPlan().assignmentWeight(variant, totalRegularWeight, WEIGHT_SCALE),
                        (left, _) -> left,
                        LinkedHashMap::new));
    }

    private void validateRegularWeights(
            UUID experimentId, List<ExperimentVariant> variants, BigDecimal totalRegularWeight) {
        boolean hasRegularVariant = variants.stream().anyMatch(ExperimentVariant::isRegular);
        if (!hasRegularVariant) {
            throw new IllegalStateException(
                    "Experiment %s must have at least one REGULAR variant for rollout".formatted(experimentId));
        }

        if (totalRegularWeight.signum() <= 0) {
            throw new IllegalStateException(
                    "Experiment %s must have positive total REGULAR weight for rollout".formatted(experimentId));
        }
    }
}
