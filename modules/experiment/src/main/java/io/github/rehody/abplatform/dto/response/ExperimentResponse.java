package io.github.rehody.abplatform.dto.response;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.List;

public record ExperimentResponse(
        String flagKey, List<ExperimentVariant> variants, ExperimentState state, long version) {
    public static ExperimentResponse from(Experiment experiment) {
        return new ExperimentResponse(
                experiment.flagKey(), experiment.variants(), experiment.state(), experiment.version());
    }
}
