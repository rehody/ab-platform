package io.github.rehody.abplatform.model.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AuditDetails(Map<String, String> values) {

    public AuditDetails {
        values = values == null ? Map.of() : copyAndValidate(values);
    }

    public static AuditDetails empty() {
        return new AuditDetails(Map.of());
    }

    public static AuditDetails entry(String key, String value) {
        return new AuditDetails(Map.of(key, value));
    }

    public static AuditDetails transition(String key, Object before, Object after) {
        return entry(key, "%s -> %s".formatted(before, after));
    }

    public static AuditDetails stateTransition(Object before, Object after) {
        return transition("state", before, after);
    }

    public static AuditDetails rolloutTransition(int before, int after) {
        return transition("rollout", before, after);
    }

    public static AuditDetails metricBindingsTransition(List<String> before, List<String> after) {
        return transition("metricBindings", before, after);
    }

    public AuditDetails with(String key, String value) {
        LinkedHashMap<String, String> updatedValues = new LinkedHashMap<>(values);
        updatedValues.put(key, value);
        return new AuditDetails(updatedValues);
    }

    private static Map<String, String> copyAndValidate(Map<String, String> values) {
        LinkedHashMap<String, String> copiedValues = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : values.entrySet()) {
            validate(entry.getKey(), "key");
            validate(entry.getValue(), "value");
            copiedValues.put(entry.getKey(), entry.getValue());
        }

        return Map.copyOf(copiedValues);
    }

    private static void validate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
