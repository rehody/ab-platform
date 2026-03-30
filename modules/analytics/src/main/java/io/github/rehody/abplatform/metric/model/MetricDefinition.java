package io.github.rehody.abplatform.metric.model;

import io.github.rehody.abplatform.metric.enums.MetricType;
import java.util.UUID;

public record MetricDefinition(UUID id, String key, String name, MetricType type) {}
