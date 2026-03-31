package io.github.rehody.abplatform.report.dto.response;

public sealed interface ExperimentMetricReportResponse
        permits CountableMetricReportResponse, UniqueMetricReportResponse {}
