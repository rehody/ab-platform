package io.github.rehody.abplatform.event.dto.request;

import java.util.UUID;

public record MetricEventCreateRequest(UUID userId, String metricKey) {}
