package io.github.rehody.abplatform.dto.response;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        int status, ErrorCode errorCode, String message, Instant timestamp, String path, List<Violation> violations) {
    public ErrorResponse {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public enum ErrorCode {
        INTERNAL_ERROR,
        VALIDATION_ERROR,
        NOT_FOUND,
        FORBIDDEN,
        BAD_REQUEST,
        CONFLICT
    }

    public record Violation(String field, String message) {}
}
