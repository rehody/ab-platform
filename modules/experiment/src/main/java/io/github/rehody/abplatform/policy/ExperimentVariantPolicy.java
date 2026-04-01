package io.github.rehody.abplatform.policy;

import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExperimentVariantPolicy {

    private static final String CONTROL_KEY = "control";

    public void validateVariantConfiguration(
            UUID experimentId, List<ExperimentVariant> variants, FeatureValue defaultValue) {
        int controlCount = 0;
        int regularCount = 0;
        Set<String> uniqueValues = new HashSet<>();
        String duplicateValueKey = null;

        for (ExperimentVariant variant : variants) {
            if (variant.isControl()) {
                controlCount++;
                validateControlVariant(experimentId, variant, defaultValue);
            } else {
                regularCount++;
                validateRegularVariant(experimentId, variant);
            }

            String normalizedValueKey = normalizeValueKey(variant.value());
            boolean added = uniqueValues.add(normalizedValueKey);
            if (!added && duplicateValueKey == null) {
                duplicateValueKey = normalizedValueKey;
            }
        }

        if (controlCount != 1) {
            throw new IllegalArgumentException(
                    "Experiment %s must contain exactly one CONTROL variant".formatted(experimentId));
        }

        if (regularCount < 1) {
            throw new IllegalArgumentException(
                    "Experiment %s must contain at least one REGULAR variant".formatted(experimentId));
        }

        if (duplicateValueKey != null) {
            throw new IllegalArgumentException(
                    "Duplicate variant value for experiment %s: %s".formatted(experimentId, duplicateValueKey));
        }
    }

    public void validateResolvableVariantConfiguration(UUID experimentId, List<ExperimentVariant> variants) {
        long controlCount =
                variants.stream().filter(ExperimentVariant::isControl).count();

        if (controlCount != 1) {
            throw new IllegalStateException(
                    "Running experiment %s must contain exactly one CONTROL variant".formatted(experimentId));
        }

        boolean hasRegularVariant = variants.stream().anyMatch(ExperimentVariant::isRegular);
        if (!hasRegularVariant) {
            throw new IllegalStateException(
                    "Running experiment %s must contain at least one REGULAR variant".formatted(experimentId));
        }
    }

    private void validateControlVariant(UUID experimentId, ExperimentVariant variant, FeatureValue defaultValue) {
        if (!CONTROL_KEY.equals(normalizeKey(variant.key()))) {
            throw new IllegalArgumentException(
                    "CONTROL variant key must be 'control' for experiment %s".formatted(experimentId));
        }

        if (!sameFeatureValue(variant.value(), defaultValue)) {
            throw new IllegalArgumentException(
                    "CONTROL variant value must match default flag value for experiment %s".formatted(experimentId));
        }
    }

    private void validateRegularVariant(UUID experimentId, ExperimentVariant variant) {
        if (CONTROL_KEY.equals(normalizeKey(variant.key()))) {
            throw new IllegalArgumentException(
                    "REGULAR variant key must not be 'control' for experiment %s".formatted(experimentId));
        }

        if (variant.type() != ExperimentVariantType.REGULAR) {
            throw new IllegalArgumentException(
                    "Unexpected variant type %s for experiment %s".formatted(variant.type(), experimentId));
        }
    }

    private boolean sameFeatureValue(FeatureValue left, FeatureValue right) {
        if (left == null || right == null) {
            return false;
        }

        if (left.type() != right.type()) {
            return false;
        }

        return normalizeValue(left).equals(normalizeValue(right));
    }

    private String normalizeValueKey(FeatureValue featureValue) {
        return "%s:%s".formatted(featureValue.type(), normalizeValue(featureValue));
    }

    private String normalizeValue(FeatureValue featureValue) {
        return switch (featureValue.type()) {
            case STRING -> String.valueOf(featureValue.value());
            case BOOL -> Boolean.toString(Boolean.parseBoolean(String.valueOf(featureValue.value())));
            case NUMBER ->
                new BigDecimal(String.valueOf(featureValue.value()))
                        .stripTrailingZeros()
                        .toPlainString();
        };
    }

    private String normalizeKey(String key) {
        return key.trim();
    }
}
