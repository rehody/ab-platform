package io.github.rehody.abplatform.report.dto.response;

import io.github.rehody.abplatform.report.model.CountableMetricReport;
import io.github.rehody.abplatform.report.model.UniqueMetricReport;

public sealed interface ExperimentMetricReportResponse
        permits CountableMetricReportResponse, UniqueMetricReportResponse {

    static ExperimentMetricReportResponse from(
            io.github.rehody.abplatform.report.model.ExperimentMetricReport experimentMetricReport) {
        if (experimentMetricReport instanceof CountableMetricReport countableMetricReport) {
            return CountableMetricReportResponse.from(countableMetricReport);
        }
        if (experimentMetricReport instanceof UniqueMetricReport uniqueMetricReport) {
            return UniqueMetricReportResponse.from(uniqueMetricReport);
        }
        throw new IllegalArgumentException(
                "Unsupported experiment metric report type: %s".formatted(experimentMetricReport.getClass()));
    }
}
