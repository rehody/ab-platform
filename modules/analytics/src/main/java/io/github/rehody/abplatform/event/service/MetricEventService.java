package io.github.rehody.abplatform.event.service;

import io.github.rehody.abplatform.event.dto.request.MetricEventCreateRequest;
import io.github.rehody.abplatform.event.dto.response.MetricEventResponse;
import io.github.rehody.abplatform.event.model.MetricEvent;
import io.github.rehody.abplatform.event.repository.MetricEventRepository;
import io.github.rehody.abplatform.exception.MetricDefinitionNotFoundException;
import io.github.rehody.abplatform.exception.MetricEventAlreadyExistsException;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.repository.MetricDefinitionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricEventService {

    private final MetricDefinitionRepository metricDefinitionRepository;
    private final MetricEventRepository metricEventRepository;

    public MetricEventResponse create(MetricEventCreateRequest request) {
        MetricDefinition metricDefinition = findMetricDefinitionOrThrow(request.metricKey());
        MetricEvent metricEvent =
                switch (metricDefinition.type()) {
                    case COUNTABLE -> createMetricEvent(request.userId(), metricDefinition);
                    case UNIQUE -> createUniqueMetricEvent(request.userId(), metricDefinition);
                };

        metricEventRepository.save(metricEvent);
        return new MetricEventResponse(metricEvent.id());
    }

    private MetricDefinition findMetricDefinitionOrThrow(String metricKey) {
        return metricDefinitionRepository
                .findByKey(metricKey)
                .orElseThrow(() ->
                        new MetricDefinitionNotFoundException("Metric definition '%s' not found".formatted(metricKey)));
    }

    private MetricEvent createUniqueMetricEvent(UUID userId, MetricDefinition metricDefinition) {
        if (metricEventRepository.existsUniqueEventForUser(userId, metricDefinition.key())) {
            throw new MetricEventAlreadyExistsException("Metric event for user '%s' and metric '%s' already exists"
                    .formatted(userId, metricDefinition.key()));
        }

        return createMetricEvent(userId, metricDefinition);
    }

    private MetricEvent createMetricEvent(UUID userId, MetricDefinition metricDefinition) {
        return new MetricEvent(UUID.randomUUID(), userId, metricDefinition.key(), Instant.now());
    }
}
