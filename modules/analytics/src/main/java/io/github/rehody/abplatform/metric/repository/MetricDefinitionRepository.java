package io.github.rehody.abplatform.metric.repository;

import io.github.rehody.abplatform.metric.model.MetricDefinition;
import java.util.Optional;

public interface MetricDefinitionRepository {

    Optional<MetricDefinition> findByKey(String key);
}
