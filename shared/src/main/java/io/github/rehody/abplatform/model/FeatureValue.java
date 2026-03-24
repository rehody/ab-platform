package io.github.rehody.abplatform.model;

public record FeatureValue(Object value, FeatureValueType type) {
    public enum FeatureValueType {
        NUMBER,
        STRING,
        BOOL
    }
}
