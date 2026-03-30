package io.github.rehody.abplatform.event.model;

import io.github.rehody.abplatform.metric.model.MetricDefinition;
import java.time.Instant;
import java.util.UUID;

public record MetricEvent(UUID id, UUID userId, MetricDefinition definition, Instant timestamp) {}
