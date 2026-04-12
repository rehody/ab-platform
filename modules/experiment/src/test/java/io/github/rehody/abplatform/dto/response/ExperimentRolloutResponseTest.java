package io.github.rehody.abplatform.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentRolloutResponseTest {

    @Test
    void from_shouldMapRunningExperimentAndAvailableActions() {
        Experiment experiment = experiment(ExperimentState.RUNNING, ExperimentRolloutPlan.of(15, true, false), 7L);

        ExperimentRolloutResponse response = ExperimentRolloutResponse.from(experiment);

        assertThat(response.version()).isEqualTo(7L);
        assertThat(response.rolloutPlan().currentStepPercentage()).isEqualTo(15);
        assertThat(response.rolloutPlan().afterRollback()).isTrue();
        assertThat(response.rolloutPlan().stillNegativeAfterRollback()).isFalse();
        assertThat(response.availableActions().canAdvance()).isTrue();
        assertThat(response.availableActions().canRollback()).isTrue();
    }

    @Test
    void from_shouldExposeUnavailableRollbackOnFirstRunningStep() {
        Experiment experiment = experiment(ExperimentState.RUNNING, ExperimentRolloutPlan.initial(), 3L);

        ExperimentRolloutResponse response = ExperimentRolloutResponse.from(experiment);

        assertThat(response.availableActions().canAdvance()).isTrue();
        assertThat(response.availableActions().canRollback()).isFalse();
    }

    @Test
    void from_shouldDisableActionsWhenExperimentIsNotRunning() {
        Experiment experiment = experiment(ExperimentState.PAUSED, ExperimentRolloutPlan.of(100, false, false), 3L);

        ExperimentRolloutResponse response = ExperimentRolloutResponse.from(experiment);

        assertThat(response.availableActions().canAdvance()).isFalse();
        assertThat(response.availableActions().canRollback()).isFalse();
    }

    @Test
    void from_shouldExposeUnavailableAdvanceForRunningExperimentOnLastStep() {
        Experiment experiment = experiment(ExperimentState.RUNNING, ExperimentRolloutPlan.of(100, false, false), 3L);

        ExperimentRolloutResponse response = ExperimentRolloutResponse.from(experiment);

        assertThat(response.availableActions().canAdvance()).isFalse();
        assertThat(response.availableActions().canRollback()).isTrue();
    }

    private Experiment experiment(ExperimentState state, ExperimentRolloutPlan rolloutPlan, long version) {
        return new Experiment(
                UUID.randomUUID(), "flag-orders", "CHECKOUT", rolloutPlan, List.of(), state, version, null, null);
    }
}
