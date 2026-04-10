package io.github.rehody.abplatform.dto.response;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;

public record ExperimentRolloutResponse(
        RolloutPlanResponse rolloutPlan, AvailableActions availableActions, long version) {

    public static ExperimentRolloutResponse from(Experiment experiment) {
        boolean running = experiment.isRunning();
        ExperimentRolloutPlan rolloutPlan = experiment.rolloutPlan();

        return new ExperimentRolloutResponse(
                RolloutPlanResponse.from(rolloutPlan),
                new AvailableActions(running && rolloutPlan.canAdvance(), running && rolloutPlan.canRollback()),
                experiment.version());
    }

    public record RolloutPlanResponse(
            int currentStepPercentage, boolean afterRollback, boolean stillNegativeAfterRollback) {
        private static RolloutPlanResponse from(ExperimentRolloutPlan rolloutPlan) {
            return new RolloutPlanResponse(
                    rolloutPlan.regularRolloutPercentage(),
                    rolloutPlan.afterRollback(),
                    rolloutPlan.stillNegativeAfterRollback());
        }
    }

    public record AvailableActions(boolean canAdvance, boolean canRollback) {}
}
