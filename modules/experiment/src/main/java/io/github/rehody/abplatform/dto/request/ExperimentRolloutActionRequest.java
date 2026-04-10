package io.github.rehody.abplatform.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ExperimentRolloutActionRequest(
        @NotNull(message = "version is required") @PositiveOrZero(message = "version must be >= 0") Long version) {}
