package io.github.rehody.abplatform.report.dto.response;

public sealed interface ExperimentReportResponse
        permits CountableMetricExperimentReportResponse, UniqueMetricExperimentReportResponse {}
