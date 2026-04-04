package io.github.rehody.abplatform.binding.dto.response;

import java.util.List;
import java.util.UUID;

public record ExperimentMetricsResponse(UUID experimentId, List<String> metricKeys) {}
