package io.github.rehody.abplatform.event.dto.response;

import io.github.rehody.abplatform.event.model.MetricEvent;
import java.util.UUID;

public record MetricEventResponse(UUID eventId) {

    public static MetricEventResponse from(MetricEvent metricEvent) {
        return new MetricEventResponse(metricEvent.id());
    }
}
