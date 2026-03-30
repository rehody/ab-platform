package io.github.rehody.abplatform.event.dto.request;

import java.util.UUID;

public record MetricEventSaveRequest(UUID userId, String metricKey) {}
