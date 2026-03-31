package io.github.rehody.abplatform.policy;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ExperimentTimestampPolicy {

    public Experiment initializeTimestamps(Experiment experiment, Instant timestamp) {
        return switch (experiment.state()) {
            case RUNNING, PAUSED -> setStartedAtIfMissing(experiment, timestamp);
            case COMPLETED, ARCHIVED ->
                setCompletedAtIfMissing(setStartedAtIfMissing(experiment, timestamp), timestamp);
            default -> experiment;
        };
    }

    public Experiment applyTransitionTimestamps(Experiment current, Experiment transitioned, Instant timestamp) {
        Experiment updated = transitioned;

        if (current.state() == ExperimentState.APPROVED && transitioned.state() == ExperimentState.RUNNING) {
            updated = setStartedAtIfMissing(updated, timestamp);
        }

        if (transitioned.state() == ExperimentState.COMPLETED) {
            updated = setCompletedAtIfMissing(updated, timestamp);
        }

        return updated;
    }

    private Experiment setStartedAtIfMissing(Experiment experiment, Instant timestamp) {
        if (experiment.startedAt() == null) {
            return experiment.withStartedAt(timestamp);
        }

        return experiment;
    }

    private Experiment setCompletedAtIfMissing(Experiment experiment, Instant timestamp) {
        if (experiment.completedAt() == null) {
            return experiment.withCompletedAt(timestamp);
        }

        return experiment;
    }
}
