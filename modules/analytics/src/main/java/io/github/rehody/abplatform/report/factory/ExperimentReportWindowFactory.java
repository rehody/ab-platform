package io.github.rehody.abplatform.report.factory;

import io.github.rehody.abplatform.exception.ExperimentReportUnavailableException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ExperimentReportWindowFactory {

    public ExperimentReportWindow create(Experiment experiment, Instant now) {
        Instant trackedFrom = experiment.startedAt();

        if (trackedFrom == null) {
            throw new ExperimentReportUnavailableException(
                    "Experiment '%s' report is unavailable before start".formatted(experiment.id()));
        }

        Instant trackedTo = experiment.completedAt();
        if (trackedTo == null) {
            trackedTo = now;
        }

        if (!trackedTo.isAfter(trackedFrom)) {
            throw new IllegalStateException("Experiment '%s' report window is invalid: trackedFrom=%s, trackedTo=%s"
                    .formatted(experiment.id(), trackedFrom, trackedTo));
        }

        return new ExperimentReportWindow(trackedFrom, trackedTo);
    }
}
