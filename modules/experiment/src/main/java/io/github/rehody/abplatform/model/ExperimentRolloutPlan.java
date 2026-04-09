package io.github.rehody.abplatform.model;

import java.util.List;

public record ExperimentRolloutPlan(
        int regularRolloutPercentage, boolean isInRollbackState, boolean repeatedNegativeEvaluationAfterRollback) {

    private static final int TOTAL_PERCENTAGE = 100;

    public static final List<Integer> REGULAR_ROLLOUT_STEPS = validateSteps(List.of(5, 15, 30, 50, 75, 100));

    public ExperimentRolloutPlan {
        if (!REGULAR_ROLLOUT_STEPS.contains(regularRolloutPercentage)) {
            throw new IllegalArgumentException(
                    "Unknown rollout regularRolloutPercentage '%s'".formatted(regularRolloutPercentage));
        }
    }

    public static ExperimentRolloutPlan initial() {
        return new ExperimentRolloutPlan(REGULAR_ROLLOUT_STEPS.getFirst(), false, false);
    }

    public static ExperimentRolloutPlan of(
            int regularRolloutPercentage, boolean isInRollbackState, boolean repeatedNegativeEvaluationAfterRollback) {
        return new ExperimentRolloutPlan(
                regularRolloutPercentage, isInRollbackState, repeatedNegativeEvaluationAfterRollback);
    }

    public int controlPercentage() {
        return TOTAL_PERCENTAGE - regularRolloutPercentage;
    }

    private static List<Integer> validateSteps(List<Integer> steps) {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Regular rollout steps must not be empty");
        }

        ensureStepsStrictlyIncreasing(steps);

        if (steps.getLast() != TOTAL_PERCENTAGE) {
            throw new IllegalArgumentException("Last regular rollout step must be %s".formatted(TOTAL_PERCENTAGE));
        }

        return List.copyOf(steps);
    }

    private static void ensureStepsStrictlyIncreasing(List<Integer> steps) {
        int prev = steps.getFirst();
        for (int i = 1; i < steps.size(); i++) {
            int curr = steps.get(i);
            if (curr <= prev) {
                throw new IllegalArgumentException("Regular rollout steps must be strictly increasing");
            }
            prev = curr;
        }
    }
}
