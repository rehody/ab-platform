package io.github.rehody.abplatform.model.audit;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record AuditActor(AuditActorType type, UUID userId, String systemName) {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public AuditActor {
        Objects.requireNonNull(type, "type must not be null");
        validate(type, userId, systemName);
    }

    public static AuditActor user(UUID userId) {
        return new AuditActor(AuditActorType.USER, userId, null);
    }

    public static AuditActor user(String userId) {
        validateUserId(userId);
        return user(UUID.fromString(userId));
    }

    private static void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }

        if (!UUID_PATTERN.matcher(userId).matches()) {
            throw new IllegalArgumentException("userId must be a valid UUID");
        }
    }

    public static AuditActor system(String systemName) {
        return new AuditActor(AuditActorType.SYSTEM, null, systemName);
    }

    private static void validate(AuditActorType type, UUID userId, String systemName) {
        if (type == AuditActorType.USER) {
            validateUserId(userId);
            validateAbsent(systemName, "systemName");
            return;
        }

        validateAbsent(userId, "userId");
        validateSystemName(systemName);
    }

    private static void validateUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
    }

    private static void validateSystemName(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            throw new IllegalArgumentException("systemName must not be blank");
        }
    }

    private static void validateAbsent(Object value, String fieldName) {
        if (value != null) {
            throw new IllegalArgumentException(fieldName + " must be null");
        }
    }
}
