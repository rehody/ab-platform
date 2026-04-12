package io.github.rehody.abplatform.rollout.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExperimentRolloutDecisionTest {

    @Test
    void values_shouldExposeAllRolloutDecisionsInDeclaredOrder() {
        assertThat(ExperimentRolloutDecision.values())
                .containsExactly(
                        ExperimentRolloutDecision.ADVANCE,
                        ExperimentRolloutDecision.HOLD,
                        ExperimentRolloutDecision.ROLLBACK,
                        ExperimentRolloutDecision.PAUSE);
    }
}
