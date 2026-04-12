package io.github.rehody.abplatform.metric.dto.request;

import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record MetricDefinitionCreateRequest(
        @NotBlank(message = "key is required") String key,
        @NotBlank(message = "name is required") String name,
        @NotNull(message = "type is required") MetricType type,
        @NotNull(message = "direction is required") MetricDirection direction,
        @NotNull(message = "severity is required") MetricSeverity severity,

        @NotNull(message = "deviationThreshold is required") @DecimalMin(value = "0.0001", message = "deviationThreshold must be > 0") BigDecimal deviationThreshold) {}
