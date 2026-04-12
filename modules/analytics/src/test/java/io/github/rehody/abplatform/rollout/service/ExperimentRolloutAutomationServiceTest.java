package io.github.rehody.abplatform.rollout.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import io.github.rehody.abplatform.rollout.enums.ExperimentRolloutDecision;
import io.github.rehody.abplatform.rollout.policy.ExperimentRolloutPolicy;
import io.github.rehody.abplatform.service.ExperimentRuntimeService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentRolloutAutomationServiceTest {

    @Mock
    private ExperimentRolloutPolicy experimentRolloutPolicy;

    @Mock
    private ExperimentRuntimeService experimentRuntimeService;

    private ExperimentRolloutAutomationService experimentRolloutAutomationService;

    @BeforeEach
    void setUp() {
        experimentRolloutAutomationService =
                new ExperimentRolloutAutomationService(experimentRolloutPolicy, experimentRuntimeService);
    }

    @Test
    void apply_shouldAdvanceRolloutWhenPolicyReturnsAdvance() {
        Experiment experiment = experiment();
        when(experimentRolloutPolicy.decide(experiment, List.of())).thenReturn(ExperimentRolloutDecision.ADVANCE);

        experimentRolloutAutomationService.apply(experiment, List.of());

        verify(experimentRuntimeService).autoAdvanceRollout(eq(experiment.id()), any());
    }

    @Test
    void apply_shouldRollbackRolloutWhenPolicyReturnsRollback() {
        Experiment experiment = experiment();
        when(experimentRolloutPolicy.decide(experiment, List.of())).thenReturn(ExperimentRolloutDecision.ROLLBACK);

        experimentRolloutAutomationService.apply(experiment, List.of());

        verify(experimentRuntimeService).autoRollbackRollout(eq(experiment.id()), any());
    }

    @Test
    void apply_shouldPauseExperimentWhenPolicyReturnsPause() {
        Experiment experiment = experiment();
        when(experimentRolloutPolicy.decide(experiment, List.of())).thenReturn(ExperimentRolloutDecision.PAUSE);

        experimentRolloutAutomationService.apply(experiment, List.of());

        verify(experimentRuntimeService).pauseOnNegativeAfterRollback(eq(experiment.id()), any());
    }

    @Test
    void apply_shouldDoNothingWhenPolicyReturnsHold() {
        Experiment experiment = experiment();
        when(experimentRolloutPolicy.decide(experiment, List.of())).thenReturn(ExperimentRolloutDecision.HOLD);

        experimentRolloutAutomationService.apply(experiment, List.of());

        verify(experimentRuntimeService, never()).autoAdvanceRollout(eq(experiment.id()), any());
        verify(experimentRuntimeService, never()).autoRollbackRollout(eq(experiment.id()), any());
        verify(experimentRuntimeService, never()).pauseOnNegativeAfterRollback(eq(experiment.id()), any());
    }

    private Experiment experiment() {
        return new Experiment(
                UUID.randomUUID(),
                "flag-orders",
                "CHECKOUT",
                ExperimentRolloutPlan.initial(),
                List.of(),
                ExperimentState.RUNNING,
                3L,
                null,
                null);
    }
}
