package io.github.rehody.abplatform.evaluation.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.binding.service.ExperimentMetricBindingService;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.service.ExperimentService;
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
    private ExperimentService experimentService;

    @Mock
    private ExperimentMetricBindingService experimentMetricBindingService;

    @Mock
    private ExperimentMetricEvaluationService experimentMetricEvaluationService;

    private ExperimentMetricEvaluationBatchService experimentMetricEvaluationBatchService;

    @BeforeEach
    void setUp() {
        experimentMetricEvaluationBatchService = new ExperimentMetricEvaluationBatchService(
                experimentService, experimentMetricBindingService, experimentMetricEvaluationService);
    }

    @Test
    void evaluateRunningExperiments_shouldLoadOnlyRunningExperiments() {
        Experiment experiment = new Experiment(
                UUID.randomUUID(), "flag-a", "CHECKOUT", List.of(), ExperimentState.RUNNING, 3L, null, null);
        when(experimentService.getRunning()).thenReturn(List.of(experiment));
        when(experimentMetricBindingService.getMetricKeys(experiment.id())).thenReturn(List.of("metric-a", "metric-b"));

        experimentMetricEvaluationBatchService.evaluateRunningExperiments();

        verify(experimentService).getRunning();
        verify(experimentMetricEvaluationService).evaluateAndApplyRisk(experiment, "metric-a");
        verify(experimentMetricEvaluationService).evaluateAndApplyRisk(experiment, "metric-b");
    }

    @Test
    void evaluateRunningExperiments_shouldContinueWhenMetricEvaluationFails() {
        Experiment experiment = new Experiment(
                UUID.randomUUID(), "flag-b", "CHECKOUT", List.of(), ExperimentState.RUNNING, 5L, null, null);
        when(experimentService.getRunning()).thenReturn(List.of(experiment));
        when(experimentMetricBindingService.getMetricKeys(experiment.id())).thenReturn(List.of("metric-a", "metric-b"));
        doThrow(new IllegalStateException("boom"))
                .when(experimentMetricEvaluationService)
                .evaluateAndApplyRisk(experiment, "metric-a");

        experimentMetricEvaluationBatchService.evaluateRunningExperiments();

        verify(experimentMetricEvaluationService).evaluateAndApplyRisk(experiment, "metric-a");
        verify(experimentMetricEvaluationService).evaluateAndApplyRisk(experiment, "metric-b");
    }
}
