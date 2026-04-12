package io.github.rehody.abplatform.report.controller;

import io.github.rehody.abplatform.report.dto.response.ExperimentMetricReportResponse;
import io.github.rehody.abplatform.report.model.ExperimentMetricReport;
import io.github.rehody.abplatform.report.service.ExperimentReportService;
import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/experiments")
@RequiredArgsConstructor
public class ExperimentReportController {

    private final ExperimentReportService experimentReportService;

    @GetMapping("/{experimentId}/metrics/{metricKey}")
    @RequiresPlatformPermission(PlatformPermission.VIEW_REPORTS)
    public ExperimentMetricReportResponse getExperimentReport(
            @PathVariable UUID experimentId, @PathVariable String metricKey) {
        ExperimentMetricReport experimentMetricReport =
                experimentReportService.getExperimentReport(experimentId, metricKey);
        return ExperimentMetricReportResponse.from(experimentMetricReport);
    }
}
