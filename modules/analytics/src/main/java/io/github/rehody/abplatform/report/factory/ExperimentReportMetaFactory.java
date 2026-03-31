package io.github.rehody.abplatform.report.factory;

import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.report.dto.response.ExperimentMetricReportMeta;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import org.springframework.stereotype.Component;

@Component
public class ExperimentReportMetaFactory {

    public ExperimentMetricReportMeta create(
            Experiment experiment, MetricDefinition metricDefinition, ExperimentReportWindow reportWindow) {
        return new ExperimentMetricReportMeta(
                experiment.id(),
                experiment.flagKey(),
                metricDefinition.key(),
                metricDefinition.type(),
                experiment.state(),
                experiment.startedAt(),
                experiment.completedAt(),
                reportWindow.trackedFrom(),
                reportWindow.trackedTo());
    }
}
