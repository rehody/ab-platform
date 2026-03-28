package io.github.rehody.abplatform.dto.response;

import io.github.rehody.abplatform.model.FeatureValue;

public record AssignmentResponse(FeatureValue value) {
    public static AssignmentResponse of(FeatureValue value) {
        return new AssignmentResponse(value);
    }
}
