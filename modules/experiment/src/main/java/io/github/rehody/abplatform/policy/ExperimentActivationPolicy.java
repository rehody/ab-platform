package io.github.rehody.abplatform.policy;

import io.github.rehody.abplatform.model.Experiment;

public interface ExperimentActivationPolicy {

    void validateActivation(Experiment experiment);
}
