package io.github.rehody.abplatform.dto.request;

import io.github.rehody.abplatform.model.FeatureValue;
import java.util.List;

public record ExperimentUpdateRequest(List<FeatureValue> variants, Long version) {
    public ExperimentUpdateRequest {
        variants = variants == null ? List.of() : variants;
    }
}
