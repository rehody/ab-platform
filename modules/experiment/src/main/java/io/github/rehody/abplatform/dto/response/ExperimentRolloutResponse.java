package io.github.rehody.abplatform.dto.response;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;

public record ExperimentRolloutResponse(
        ExperimentRolloutPlan rolloutPlan, int currentStep, AvailableActions availableActions, long version) {

    public static ExperimentRolloutResponse from(Experiment experiment) {
        boolean running = experiment.isRunning();
        ExperimentRolloutPlan rolloutPlan = experiment.rolloutPlan();

        return new ExperimentRolloutResponse(
                rolloutPlan,
                rolloutPlan.regularRolloutPercentage(),
                new AvailableActions(running && rolloutPlan.canAdvance(), running && rolloutPlan.canRollback()),
                experiment.version());
    }

    public record AvailableActions(boolean canAdvance, boolean canRollback) {}
}
