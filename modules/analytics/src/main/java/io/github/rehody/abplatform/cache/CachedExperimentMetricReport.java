package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.report.model.CountableMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReport;
import io.github.rehody.abplatform.report.model.UniqueMetricReport;

public record CachedExperimentMetricReport(
        CachedCountableMetricReport countableMetricReport, CachedUniqueMetricReport uniqueMetricReport) {

    public CachedExperimentMetricReport {
        boolean hasCountableMetricReport = countableMetricReport != null;
        boolean hasUniqueMetricReport = uniqueMetricReport != null;

        if (hasCountableMetricReport == hasUniqueMetricReport) {
            throw new IllegalArgumentException("Cached experiment metric report must contain exactly one report");
        }
    }

    public static CachedExperimentMetricReport from(ExperimentMetricReport experimentMetricReport) {
        if (experimentMetricReport == null) {
            throw new IllegalArgumentException("Experiment metric report must not be null");
        }

        if (experimentMetricReport instanceof CountableMetricReport countableMetricReport) {
            return of(CachedCountableMetricReport.from(countableMetricReport));
        }

        return of(CachedUniqueMetricReport.from((UniqueMetricReport) experimentMetricReport));
    }

    public static CachedExperimentMetricReport of(CachedCountableMetricReport cachedCountableMetricReport) {
        return new CachedExperimentMetricReport(cachedCountableMetricReport, null);
    }

    public static CachedExperimentMetricReport of(CachedUniqueMetricReport cachedUniqueMetricReport) {
        return new CachedExperimentMetricReport(null, cachedUniqueMetricReport);
    }

    public ExperimentMetricReport toModel() {
        if (countableMetricReport != null) {
            return countableMetricReport.toModel();
        }
        return uniqueMetricReport.toModel();
    }
}
