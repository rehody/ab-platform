package io.github.rehody.abplatform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignmentRequest(
        @NotNull(message = "userId is required") UUID userId,
        @NotBlank(message = "flagKey is required") String flagKey) {}
