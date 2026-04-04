package io.github.rehody.abplatform.evaluation.builder;

import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationMeta;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import org.springframework.stereotype.Component;

@Component
public class ExperimentMetricEvaluationMetaFactory {

    public ExperimentMetricEvaluationMeta create(
            Experiment experiment, MetricDefinition metricDefinition, ExperimentReportWindow reportWindow) {
        return new ExperimentMetricEvaluationMeta(
                experiment.id(),
                experiment.flagKey(),
                metricDefinition.key(),
                metricDefinition.direction(),
                metricDefinition.severity(),
                metricDefinition.deviationThreshold(),
                experiment.state(),
                experiment.startedAt(),
                experiment.completedAt(),
                reportWindow.trackedFrom(),
                reportWindow.trackedTo());
    }
}
