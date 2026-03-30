package io.github.rehody.abplatform.report.controller;

import io.github.rehody.abplatform.report.dto.response.ExperimentReportResponse;
import io.github.rehody.abplatform.report.service.ReportService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/{experimentId}/metrics{metricKey}")
    public ExperimentReportResponse getReport(@PathVariable UUID experimentId, @PathVariable String metricKey) {
        return reportService.getReport(experimentId, metricKey);
    }
}
