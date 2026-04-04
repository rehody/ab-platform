package io.github.rehody.abplatform.model;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.exception.ExperimentStateTransitionException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record Experiment(
        UUID id,
        String flagKey,
        String domain,
        List<ExperimentVariant> variants,
        ExperimentState state,
        long version,
        Instant startedAt,
        Instant completedAt) {

    public Experiment {
        variants = List.copyOf(variants);
    }

    public boolean isRunning() {
        return state == ExperimentState.RUNNING;
    }

    public boolean isDraft() {
        return state == ExperimentState.DRAFT;
    }

    public boolean isApproved() {
        return state == ExperimentState.APPROVED;
    }

    public boolean isCompleted() {
        return state == ExperimentState.COMPLETED;
    }

    public boolean isArchived() {
        return state == ExperimentState.ARCHIVED;
    }

    public boolean isTerminal() {
        return isCompleted() || isArchived();
    }

    public Experiment withFlagKey(String flagKey) {
        return new Experiment(id, flagKey, domain, variants, state, version, startedAt, completedAt);
    }

    public Experiment withDomainKey(String domain) {
        return new Experiment(id, flagKey, domain, variants, state, version, startedAt, completedAt);
    }

    public Experiment withVariants(List<ExperimentVariant> variants) {
        return new Experiment(id, flagKey, domain, variants, state, version, startedAt, completedAt);
    }

    public Experiment withVersion(long version) {
        return new Experiment(id, flagKey, domain, variants, state, version, startedAt, completedAt);
    }

    public Experiment withStartedAt(Instant startedAt) {
        return new Experiment(id, flagKey, domain, variants, state, version, startedAt, completedAt);
    }

    public Experiment withCompletedAt(Instant completedAt) {
        return new Experiment(id, flagKey, domain, variants, state, version, startedAt, completedAt);
    }

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

        return new Experiment(id, flagKey, domain, variants, targetState, version, startedAt, completedAt);
    }
}
