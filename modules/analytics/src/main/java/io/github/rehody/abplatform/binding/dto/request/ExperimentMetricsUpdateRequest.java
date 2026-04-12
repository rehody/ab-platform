package io.github.rehody.abplatform.binding.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ExperimentMetricsUpdateRequest(
        @NotNull(message = "metricKeys is required") List<@NotNull(message = "metricKey is required") String> metricKeys) {}
