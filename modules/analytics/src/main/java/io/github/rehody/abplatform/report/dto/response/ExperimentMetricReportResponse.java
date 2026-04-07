package io.github.rehody.abplatform.report.dto.response;

import io.github.rehody.abplatform.report.model.CountableMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReport;
import io.github.rehody.abplatform.report.model.UniqueMetricReport;

public sealed interface ExperimentMetricReportResponse
        permits CountableMetricReportResponse, UniqueMetricReportResponse {

    static ExperimentMetricReportResponse from(ExperimentMetricReport experimentMetricReport) {
        if (experimentMetricReport == null) {
            throw new IllegalArgumentException("Experiment metric report must not be null");
        }

        if (experimentMetricReport instanceof CountableMetricReport countableMetricReport) {
            return CountableMetricReportResponse.from(countableMetricReport);
        }

        return UniqueMetricReportResponse.from((UniqueMetricReport) experimentMetricReport);
    }
}
