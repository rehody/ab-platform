package io.github.rehody.abplatform.service.snapshot;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

        BigDecimal regularPercentage =
                BigDecimal.valueOf(experiment.rolloutPlan().regularRolloutPercentage());

        BigDecimal controlPercentage =
                BigDecimal.valueOf(experiment.rolloutPlan().controlPercentage());

        return variants.stream()
                .collect(Collectors.toMap(
                        ExperimentVariant::id,
                        variant -> resolveWeight(variant, totalRegularWeight, regularPercentage, controlPercentage),
                        (left, _) -> left,
                        LinkedHashMap::new));
    }

    private BigDecimal resolveWeight(
            ExperimentVariant variant,
            BigDecimal totalRegularWeight,
            BigDecimal regularPercentage,
            BigDecimal controlPercentage) {

        if (variant.isControl()) {
            return controlPercentage;
        }
        return scaleWeight(variant.weight(), totalRegularWeight, regularPercentage);
    }

    private BigDecimal scaleWeight(
            BigDecimal regularWeight, BigDecimal totalRegularWeight, BigDecimal regularPercentage) {
        return regularWeight.multiply(regularPercentage).divide(totalRegularWeight, WEIGHT_SCALE, RoundingMode.HALF_UP);
    }
}
