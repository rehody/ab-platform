package io.github.rehody.abplatform.dto.request;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.validation.ValidExperimentVariant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ExperimentCreateRequest(
        @NotBlank(message = "flagKey is required") String flagKey,

        @NotNull(message = "variants is required") List<@NotNull(message = "variant is required") @ValidExperimentVariant ExperimentVariant> variants,

        @NotNull(message = "state is required") ExperimentState state) {}
