package io.github.rehody.abplatform.model;

public record FeatureValue(Object value, FeatureValueType type) {

    public boolean hasMatchingType() {
        if (type == null || value == null) {
            return false;
        }

        return switch (type) {
            case BOOL -> value instanceof Boolean;
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number;
        };
    }

    public enum FeatureValueType {
        NUMBER,
        STRING,
        BOOL
    }
}
