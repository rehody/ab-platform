package io.github.rehody.abplatform.util.lock;

import java.util.Locale;
import java.util.regex.Pattern;

public record LockNamespace(String value) {

    private static final Pattern VALID_NAMESPACE = Pattern.compile("^[a-z0-9]+(?:[._-][a-z0-9]+)*$");

    public LockNamespace {
        value = normalize(value);
    }

    public static LockNamespace of(String value) {
        return new LockNamespace(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("namespace is required");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("namespace is required");
        }
        if (!VALID_NAMESPACE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("namespace must match pattern %s".formatted(VALID_NAMESPACE.pattern()));
        }

        return normalized;
    }
}
