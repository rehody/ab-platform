package io.github.rehody.abplatform.repository.validation;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExperimentVariantPreparer {

    public List<ExperimentVariant> prepare(UUID experimentId, List<ExperimentVariant> variants) {
        List<ExperimentVariant> result = new ArrayList<>(variants.size());
        Set<String> keys = new HashSet<>();

        for (int position = 0; position < variants.size(); position++) {
            ExperimentVariant variant = variants.get(position);
            String normalizedKey = normalizeKey(variant.key());
            boolean added = keys.add(normalizedKey);

            if (!added) {
                throw new IllegalArgumentException(
                        "Duplicate variant key for experiment %s: %s".formatted(experimentId, normalizedKey));
            }

            result.add(new ExperimentVariant(
                    resolveId(variant.id()),
                    normalizedKey,
                    variant.value(),
                    position,
                    variant.weight(),
                    variant.variantType()));
        }

        return result;
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
