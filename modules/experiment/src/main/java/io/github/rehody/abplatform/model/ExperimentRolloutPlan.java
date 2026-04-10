package io.github.rehody.abplatform.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record ExperimentRolloutPlan(
        int regularRolloutPercentage, boolean afterRollback, boolean stillNegativeAfterRollback) {

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
            int regularRolloutPercentage, boolean afterRollback, boolean stillNegativeAfterRollback) {
        return new ExperimentRolloutPlan(regularRolloutPercentage, afterRollback, stillNegativeAfterRollback);
    }

    public int controlPercentage() {
        return TOTAL_PERCENTAGE - regularRolloutPercentage;
    }

    public int regularBucketPoolSize(int bucketPoolSize) {
        return bucketPoolSize * regularRolloutPercentage / TOTAL_PERCENTAGE;
    }

    public int controlBucketPoolSize(int bucketPoolSize) {
        return bucketPoolSize - regularBucketPoolSize(bucketPoolSize);
    }

    public ExperimentRolloutPlan advance() {
        return new ExperimentRolloutPlan(nextStep(), false, false);
    }

    public ExperimentRolloutPlan rollback() {
        return new ExperimentRolloutPlan(previousStep(), true, false);
    }

    public ExperimentRolloutPlan markNegativeAfterRollback() {
        return new ExperimentRolloutPlan(regularRolloutPercentage, true, true);
    }

    public BigDecimal assignmentWeight(ExperimentVariant variant, BigDecimal totalRegularWeight, int scale) {
        if (variant.isControl()) {
            return BigDecimal.valueOf(controlPercentage());
        }

        validateTotalRegularWeight(totalRegularWeight);

        return variant.weight()
                .multiply(BigDecimal.valueOf(regularRolloutPercentage))
                .divide(totalRegularWeight, scale, RoundingMode.HALF_UP);
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

    private void validateTotalRegularWeight(BigDecimal totalRegularWeight) {
        if (totalRegularWeight == null || totalRegularWeight.signum() <= 0) {
            throw new IllegalStateException("Total REGULAR weight must be positive for rollout calculation");
        }
    }

    private int nextStep() {
        int currentStepIndex = currentStepIndex();
        if (currentStepIndex == REGULAR_ROLLOUT_STEPS.size() - 1) {
            return regularRolloutPercentage;
        }
        return REGULAR_ROLLOUT_STEPS.get(currentStepIndex + 1);
    }

    private int previousStep() {
        int currentStepIndex = currentStepIndex();
        if (currentStepIndex == 0) {
            return regularRolloutPercentage;
        }
        return REGULAR_ROLLOUT_STEPS.get(currentStepIndex - 1);
    }

    private int currentStepIndex() {
        return REGULAR_ROLLOUT_STEPS.indexOf(regularRolloutPercentage);
    }
}
