package io.github.rehody.abplatform.event.repository;

import io.github.rehody.abplatform.event.model.MetricEvent;
import java.util.UUID;

public interface MetricEventRepository {

    void save(MetricEvent metricEvent);

    boolean existsUniqueEventForUser(UUID userId, UUID metricDefinitionId);
}
