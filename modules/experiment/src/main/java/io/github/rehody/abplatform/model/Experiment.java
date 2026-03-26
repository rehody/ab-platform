package io.github.rehody.abplatform.model;

import io.github.rehody.abplatform.enums.ExperimentState;
import java.util.List;
import java.util.UUID;

public record Experiment(UUID id, String flagKey, List<FeatureValue> variants, ExperimentState state, long version) {}
