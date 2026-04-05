package io.github.rehody.abplatform.model;

import java.time.Instant;
import java.util.UUID;

public record AssignmentEvent(UUID id, UUID userId, UUID variantId, UUID experimentId, Instant timestamp) {}
