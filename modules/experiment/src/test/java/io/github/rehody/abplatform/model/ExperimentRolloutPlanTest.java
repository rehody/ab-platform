package io.github.rehody.abplatform.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExperimentRolloutPlanTest {

    @Test
    void constructor_shouldRejectUnknownRolloutPercentage() {
        assertThatThrownBy(() -> new ExperimentRolloutPlan(10, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown rollout regularRolloutPercentage '10'");
    }

    @Test
    void initial_shouldReturnFirstStepWithoutRollbackFlags() {
        ExperimentRolloutPlan rolloutPlan = ExperimentRolloutPlan.initial();

        assertThat(rolloutPlan.regularRolloutPercentage()).isEqualTo(5);
        assertThat(rolloutPlan.afterRollback()).isFalse();
        assertThat(rolloutPlan.stillNegativeAfterRollback()).isFalse();
    }

    @Test
    void percentagesAndBucketPools_shouldBeDerivedFromRegularRolloutPercentage() {
        ExperimentRolloutPlan rolloutPlan = ExperimentRolloutPlan.of(30, false, false);

        assertThat(rolloutPlan.controlPercentage()).isEqualTo(70);
        assertThat(rolloutPlan.regularBucketPoolSize(10_000)).isEqualTo(3_000);
        assertThat(rolloutPlan.controlBucketPoolSize(10_000)).isEqualTo(7_000);
    }

    @Test
    void canAdvanceAndCanRollback_shouldReflectCurrentStep() {
        assertThat(ExperimentRolloutPlan.of(5, false, false).canAdvance()).isTrue();
        assertThat(ExperimentRolloutPlan.of(5, false, false).canRollback()).isFalse();
        assertThat(ExperimentRolloutPlan.of(100, false, false).canAdvance()).isFalse();
        assertThat(ExperimentRolloutPlan.of(100, false, false).canRollback()).isTrue();
    }

    @Test
    void advanceAndRollback_shouldMoveBetweenConfiguredSteps() {
        assertThat(ExperimentRolloutPlan.of(15, false, false).advance().regularRolloutPercentage())
                .isEqualTo(30);
        assertThat(ExperimentRolloutPlan.of(15, false, false).rollback().regularRolloutPercentage())
                .isEqualTo(5);
    }

    @Test
    void advanceAndRollback_shouldStayAtBoundarySteps() {
        assertThat(ExperimentRolloutPlan.of(100, false, false).advance().regularRolloutPercentage())
                .isEqualTo(100);
        assertThat(ExperimentRolloutPlan.of(5, false, false).rollback().regularRolloutPercentage())
                .isEqualTo(5);
    }

    @Test
    void markNegativeAfterRollback_shouldSetRollbackFlags() {
        ExperimentRolloutPlan rolloutPlan =
                ExperimentRolloutPlan.of(15, true, false).markNegativeAfterRollback();

        assertThat(rolloutPlan.afterRollback()).isTrue();
        assertThat(rolloutPlan.stillNegativeAfterRollback()).isTrue();
    }

    @Test
    void assignmentWeight_shouldReturnControlPercentageForControlVariant() {
        ExperimentVariant controlVariant = new ExperimentVariant(
                UUID.randomUUID(),
                "control",
                new FeatureValue(true, FeatureValueType.BOOL),
                0,
                null,
                ExperimentVariantType.CONTROL);
        ExperimentRolloutPlan rolloutPlan = ExperimentRolloutPlan.of(30, false, false);

        BigDecimal response = rolloutPlan.assignmentWeight(controlVariant, BigDecimal.ONE, 2);

        assertThat(response).isEqualByComparingTo("70");
    }

    @Test
    void assignmentWeight_shouldReturnScaledRegularWeight() {
        ExperimentVariant regularVariant = new ExperimentVariant(
                UUID.randomUUID(),
                "variant-a",
                new FeatureValue(false, FeatureValueType.BOOL),
                1,
                new BigDecimal("2"),
                ExperimentVariantType.REGULAR);
        ExperimentRolloutPlan rolloutPlan = ExperimentRolloutPlan.of(30, false, false);

        BigDecimal response = rolloutPlan.assignmentWeight(regularVariant, new BigDecimal("4"), 2);

        assertThat(response).isEqualByComparingTo("15.00");
    }

    @Test
    void assignmentWeight_shouldRejectMissingOrNonPositiveTotalRegularWeight() {
        ExperimentVariant regularVariant = new ExperimentVariant(
                UUID.randomUUID(),
                "variant-a",
                new FeatureValue(false, FeatureValueType.BOOL),
                1,
                new BigDecimal("2"),
                ExperimentVariantType.REGULAR);
        ExperimentRolloutPlan rolloutPlan = ExperimentRolloutPlan.of(30, false, false);

        assertThatThrownBy(() -> rolloutPlan.assignmentWeight(regularVariant, null, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Total REGULAR weight must be positive for rollout calculation");
        assertThatThrownBy(() -> rolloutPlan.assignmentWeight(regularVariant, BigDecimal.ZERO, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Total REGULAR weight must be positive for rollout calculation");
    }

    @Test
    void validateSteps_shouldRejectInvalidStepLists() {
        assertThatThrownBy(
                        () -> ReflectionTestUtils.invokeMethod(ExperimentRolloutPlan.class, "validateSteps", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Regular rollout steps must not be empty");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                        ExperimentRolloutPlan.class, "validateSteps", List.of(5, 15, 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Last regular rollout step must be 100");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                        ExperimentRolloutPlan.class, "validateSteps", List.of(5, 5, 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Regular rollout steps must be strictly increasing");
    }
}
