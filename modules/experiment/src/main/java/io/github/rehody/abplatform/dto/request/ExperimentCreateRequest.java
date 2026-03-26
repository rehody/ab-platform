package io.github.rehody.abplatform.dto.request;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.FeatureValue;
import java.util.List;

public record ExperimentCreateRequest(
        String flagKey, List<FeatureValue> variants, ExperimentState state, long version) {}
