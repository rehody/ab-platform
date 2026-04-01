package io.github.rehody.abplatform.dto.request;

import java.util.UUID;

public record AssignmentRequest(UUID userId, String flagKey) {}
