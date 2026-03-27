package io.github.rehody.abplatform.repository.validation;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExperimentVariantPreparer {

    public List<ExperimentVariant> prepare(UUID experimentId, List<ExperimentVariant> variants) {
        Objects.requireNonNull(experimentId, "experimentId must not be null");
        Objects.requireNonNull(variants, "variants must not be null");

        List<ExperimentVariant> result = new ArrayList<>(variants.size());
        Set<String> keys = new HashSet<>();

        for (int position = 0; position < variants.size(); position++) {
            ExperimentVariant variant = Objects.requireNonNull(
                    variants.get(position), "variant at position %d must not be null".formatted(position));

            String normalizedKey = normalizeKey(variant.key(), experimentId);
            boolean added = keys.add(normalizedKey);

            if (!added) {
                throw new IllegalArgumentException(
                        "Duplicate variant key for experiment %s: %s".formatted(experimentId, normalizedKey));
            }

            result.add(new ExperimentVariant(resolveId(variant.id()), normalizedKey, variant.value(), position));
        }

        return result;
    }

    private String normalizeKey(String key, UUID experimentId) {
        if (key == null) {
            throw new IllegalArgumentException("Variant key must not be null for experiment " + experimentId);
        }

        String normalizedKey = key.trim();
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("Variant key must not be blank for experiment " + experimentId);
        }

        return normalizedKey;
    }

    private UUID resolveId(UUID id) {
        return id == null ? UUID.randomUUID() : id;
    }
}
