package io.github.rehody.abplatform.evaluation.controller;

import io.github.rehody.abplatform.evaluation.dto.request.ExperimentMetricRiskResolutionRequest;
import io.github.rehody.abplatform.evaluation.dto.response.ExperimentMetricRiskResponse;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.evaluation.service.ExperimentMetricRiskService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiment-risks")
@RequiredArgsConstructor
public class ExperimentMetricRiskController {

    private final ExperimentMetricRiskService experimentMetricRiskService;

    @PostMapping("/{riskId}/resolve")
    public ExperimentMetricRiskResponse resolve(
            @PathVariable UUID riskId,
            @Valid @RequestBody(required = false) ExperimentMetricRiskResolutionRequest request) {
        ExperimentMetricRisk risk =
                experimentMetricRiskService.resolve(riskId, request == null ? null : request.comment());
        return ExperimentMetricRiskResponse.from(risk);
    }
}
