package io.github.rehody.abplatform.evaluation.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.binding.service.ExperimentMetricBindingService;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import io.github.rehody.abplatform.rollout.service.ExperimentRolloutAutomationService;
import io.github.rehody.abplatform.service.ExperimentQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentMetricEvaluationBatchServiceTest {

    @Mock
    private ExperimentQueryService experimentQueryService;

    @Mock
    private ExperimentMetricBindingService experimentMetricBindingService;

    @Mock
    private ExperimentMetricEvaluationService experimentMetricEvaluationService;

    @Mock
    private ExperimentRolloutAutomationService experimentRolloutAutomationService;

    private ExperimentMetricEvaluationBatchService experimentMetricEvaluationBatchService;

    @BeforeEach
    void setUp() {
        experimentMetricEvaluationBatchService = new ExperimentMetricEvaluationBatchService(
                experimentQueryService,
                experimentMetricBindingService,
                experimentMetricEvaluationService,
                experimentRolloutAutomationService);
    }

    @Test
    void evaluateRunningExperiments_shouldLoadOnlyRunningExperiments() {
        Experiment experiment = new Experiment(
                UUID.randomUUID(),
                "flag-a",
                "CHECKOUT",
                ExperimentRolloutPlan.initial(),
                List.of(),
                ExperimentState.RUNNING,
                3L,
                null,
                null);
        when(experimentQueryService.getRunning()).thenReturn(List.of(experiment));
        when(experimentMetricBindingService.getMetricKeys(experiment.id())).thenReturn(List.of("metric-a", "metric-b"));

        experimentMetricEvaluationBatchService.evaluateRunningExperiments();

        verify(experimentQueryService).getRunning();
        verify(experimentMetricEvaluationService).evaluateAndApplyRisk(experiment.id(), "metric-a");
        verify(experimentMetricEvaluationService).evaluateAndApplyRisk(experiment.id(), "metric-b");
    }

    @Test
    void evaluateRunningExperiments_shouldContinueWhenMetricEvaluationFails() {
        Experiment experiment = new Experiment(
                UUID.randomUUID(),
                "flag-b",
                "CHECKOUT",
                ExperimentRolloutPlan.initial(),
                List.of(),
                ExperimentState.RUNNING,
                5L,
                null,
                null);
        when(experimentQueryService.getRunning()).thenReturn(List.of(experiment));
        when(experimentMetricBindingService.getMetricKeys(experiment.id())).thenReturn(List.of("metric-a", "metric-b"));
        doThrow(new IllegalStateException("boom"))
                .when(experimentMetricEvaluationService)
                .evaluateAndApplyRisk(experiment, "metric-a");

        experimentMetricEvaluationBatchService.evaluateRunningExperiments();

        verify(experimentMetricEvaluationService).evaluateAndApplyRisk(experiment.id(), "metric-a");
        verify(experimentMetricEvaluationService).evaluateAndApplyRisk(experiment.id(), "metric-b");
    }
}
