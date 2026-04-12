package io.github.rehody.abplatform.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MetricEventCreateRequest(
        @NotNull(message = "userId is required") UUID userId,
        @NotBlank(message = "metricKey is required") String metricKey) {}
