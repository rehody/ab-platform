package io.github.rehody.abplatform.dto.request;

import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.List;

public record ExperimentUpdateRequest(List<ExperimentVariant> variants, Long version) {
    public ExperimentUpdateRequest {
        variants = variants == null ? List.of() : variants;
    }
}
