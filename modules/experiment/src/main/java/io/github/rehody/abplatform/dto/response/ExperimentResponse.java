package io.github.rehody.abplatform.dto.response;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.FeatureValue;
import java.util.List;

public record ExperimentResponse(String flagKey, List<FeatureValue> variants, ExperimentState state) {
    public static ExperimentResponse from(Experiment experiment) {
        return new ExperimentResponse(experiment.flagKey(), experiment.variants(), experiment.state());
    }
}
