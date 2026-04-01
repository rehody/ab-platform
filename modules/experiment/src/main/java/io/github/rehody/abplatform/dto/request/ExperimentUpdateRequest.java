package io.github.rehody.abplatform.dto.request;

import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.validation.ValidExperimentVariant;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record ExperimentUpdateRequest(
        @NotNull(message = "variants is required") List<@NotNull(message = "variant is required") @ValidExperimentVariant ExperimentVariant> variants,

        @NotNull(message = "version is required") @PositiveOrZero(message = "version must be >= 0") Long version) {}
