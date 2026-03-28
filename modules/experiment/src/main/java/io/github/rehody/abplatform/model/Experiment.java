package io.github.rehody.abplatform.model;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.exception.ExperimentStateTransitionException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record Experiment(
        UUID id, String flagKey, List<ExperimentVariant> variants, ExperimentState state, long version) {

    public Experiment submitForReview() {
        return transition("submit for review", ExperimentState.IN_REVIEW, ExperimentState.DRAFT);
    }

    public Experiment approve() {
        return transition("approve", ExperimentState.APPROVED, ExperimentState.IN_REVIEW);
    }

    public Experiment reject() {
        return transition("reject", ExperimentState.REJECTED, ExperimentState.IN_REVIEW);
    }

    public Experiment returnToDraft() {
        return transition("return to draft", ExperimentState.DRAFT, ExperimentState.REJECTED);
    }

    public Experiment start() {
        return transition("start", ExperimentState.RUNNING, ExperimentState.APPROVED);
    }

    public Experiment pause() {
        return transition("pause", ExperimentState.PAUSED, ExperimentState.RUNNING);
    }

    public Experiment resume() {
        return transition("resume", ExperimentState.RUNNING, ExperimentState.PAUSED);
    }

    public Experiment complete() {
        return transition("complete", ExperimentState.COMPLETED, ExperimentState.RUNNING, ExperimentState.PAUSED);
    }

    public Experiment archive() {
        return transition("archive", ExperimentState.ARCHIVED, ExperimentState.COMPLETED);
    }

    private Experiment transition(String action, ExperimentState targetState, ExperimentState... allowedSourceStates) {
        Set<ExperimentState> allowedStates = EnumSet.noneOf(ExperimentState.class);
        allowedStates.addAll(List.of(allowedSourceStates));

        if (!allowedStates.contains(state)) {
            throw new ExperimentStateTransitionException("Cannot %s experiment in state %s. Allowed source states: %s"
                    .formatted(action, state, allowedStates));
        }

        return new Experiment(id, flagKey, List.copyOf(variants), targetState, version);
    }
}
