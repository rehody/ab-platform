package io.github.rehody.abplatform.evaluation.controller;

import io.github.rehody.abplatform.evaluation.dto.response.ExperimentMetricEvaluationResponse;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.service.ExperimentMetricEvaluationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/experiments")
@RequiredArgsConstructor
public class ExperimentMetricEvaluationController {

    private final ExperimentMetricEvaluationService experimentMetricEvaluationService;

    @GetMapping("/{experimentId}/metrics/{metricKey}/evaluation")
    public ExperimentMetricEvaluationResponse getEvaluationReport(
            @PathVariable UUID experimentId, @PathVariable String metricKey) {
        ExperimentMetricEvaluationReport experimentMetricEvaluationReport =
                experimentMetricEvaluationService.getEvaluationReport(experimentId, metricKey);
        return ExperimentMetricEvaluationResponse.from(experimentMetricEvaluationReport);
    }
}
