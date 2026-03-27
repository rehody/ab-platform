package io.github.rehody.abplatform.dto.request;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.List;

public record ExperimentCreateRequest(String flagKey, List<ExperimentVariant> variants, ExperimentState state) {}
