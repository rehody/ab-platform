package io.github.rehody.abplatform.event.model;

import java.time.Instant;
import java.util.UUID;

public record MetricEvent(UUID id, UUID userId, String metricKey, Instant timestamp) {}
