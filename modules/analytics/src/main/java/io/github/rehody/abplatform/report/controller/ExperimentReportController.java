package io.github.rehody.abplatform.report.controller;

import io.github.rehody.abplatform.report.dto.response.ExperimentMetricReportResponse;
import io.github.rehody.abplatform.report.service.ExperimentReportService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports/experiments")
@RequiredArgsConstructor
public class ExperimentReportController {

    private final ExperimentReportService experimentReportService;

    @GetMapping("/{experimentId}/metrics/{metricKey}")
    public ExperimentMetricReportResponse getExperimentReport(
            @PathVariable UUID experimentId, @PathVariable String metricKey) {
        return experimentReportService.getExperimentReport(experimentId, metricKey);
    }
}
