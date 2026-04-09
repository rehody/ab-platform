package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentVariantPreparer {

    private final ExperimentVariantValidator experimentVariantValidator;

    public List<ExperimentVariant> prepare(UUID experimentId, List<ExperimentVariant> variants) {
        Set<String> keys = new HashSet<>();
        List<ExperimentVariant> preparedVariants = new ArrayList<>(variants.size());

        for (int position = 0; position < variants.size(); position++) {
            ExperimentVariant variant = variants.get(position);
            ExperimentVariant resolvedVariant = resolveVariant(experimentId, variant, position, keys);
            preparedVariants.add(resolvedVariant);
        }

        return List.copyOf(preparedVariants);
    }

    private ExperimentVariant resolveVariant(
            UUID experimentId, ExperimentVariant variant, int position, Set<String> keys) {
        String normalizedKey = normalizeKey(variant.key());
        boolean added = keys.add(normalizedKey);

        if (!added) {
            throw new IllegalArgumentException(
                    "Duplicate variant key for experiment %s: %s".formatted(experimentId, normalizedKey));
        }

        experimentVariantValidator.validate(experimentId, variant);

        return new ExperimentVariant(
                resolveId(variant.id()), normalizedKey, variant.value(), position, variant.weight(), variant.type());
    }

    private String normalizeKey(String key) {
        return key.trim();
    }

    private UUID resolveId(UUID id) {
        if (id == null) {
            return UUID.randomUUID();
        }
        return id;
    }
}
