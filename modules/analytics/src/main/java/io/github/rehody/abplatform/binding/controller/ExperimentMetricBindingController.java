package io.github.rehody.abplatform.binding.controller;

import io.github.rehody.abplatform.binding.dto.request.ExperimentMetricsUpdateRequest;
import io.github.rehody.abplatform.binding.dto.response.ExperimentMetricsResponse;
import io.github.rehody.abplatform.binding.service.ExperimentMetricBindingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiments/{experimentId}/metrics")
@RequiredArgsConstructor
public class ExperimentMetricBindingController {

    private final ExperimentMetricBindingService experimentMetricBindingService;

    @PutMapping
    public ExperimentMetricsResponse updateMetricKeys(
            @PathVariable UUID experimentId, @Valid @RequestBody ExperimentMetricsUpdateRequest request) {
        List<String> metricKeys = experimentMetricBindingService.updateMetricKeys(experimentId, request.metricKeys());
        return new ExperimentMetricsResponse(experimentId, metricKeys);
    }

    @GetMapping
    public ExperimentMetricsResponse getMetricKeys(@PathVariable UUID experimentId) {
        List<String> metricKeys = experimentMetricBindingService.getMetricKeys(experimentId);
        return new ExperimentMetricsResponse(experimentId, metricKeys);
    }
}
