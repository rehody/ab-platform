package io.github.rehody.abplatform.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentTimestampPolicyTest {

    private final ExperimentTimestampPolicy experimentTimestampPolicy = new ExperimentTimestampPolicy();

    @Test
    void initializeTimestamps_shouldSetStartedAtForRunningAndPaused() {
        Instant timestamp = Instant.parse("2026-04-06T10:15:30Z");

        Experiment running = experimentTimestampPolicy.initializeTimestamps(
                experiment(ExperimentState.RUNNING, null, null), timestamp);
        Experiment paused = experimentTimestampPolicy.initializeTimestamps(
                experiment(ExperimentState.PAUSED, null, null), timestamp);

        assertThat(running.startedAt()).isEqualTo(timestamp);
        assertThat(running.completedAt()).isNull();
        assertThat(paused.startedAt()).isEqualTo(timestamp);
        assertThat(paused.completedAt()).isNull();
    }

    @Test
    void initializeTimestamps_shouldSetStartedAtAndCompletedAtForCompletedAndArchived() {
        Instant timestamp = Instant.parse("2026-04-06T10:15:30Z");

        Experiment completed = experimentTimestampPolicy.initializeTimestamps(
                experiment(ExperimentState.COMPLETED, null, null), timestamp);
        Experiment archived = experimentTimestampPolicy.initializeTimestamps(
                experiment(ExperimentState.ARCHIVED, null, null), timestamp);

        assertThat(completed.startedAt()).isEqualTo(timestamp);
        assertThat(completed.completedAt()).isEqualTo(timestamp);
        assertThat(archived.startedAt()).isEqualTo(timestamp);
        assertThat(archived.completedAt()).isEqualTo(timestamp);
    }

    @Test
    void initializeTimestamps_shouldKeepDraftLikeStatesUnchanged() {
        Instant timestamp = Instant.parse("2026-04-06T10:15:30Z");
        Experiment draft = experiment(ExperimentState.DRAFT, null, null);

        Experiment initialized = experimentTimestampPolicy.initializeTimestamps(draft, timestamp);

        assertThat(initialized).isEqualTo(draft);
    }

    @Test
    void initializeTimestamps_shouldNotOverwriteExistingTimestamps() {
        Instant startedAt = Instant.parse("2026-04-06T09:00:00Z");
        Instant completedAt = Instant.parse("2026-04-06T09:30:00Z");
        Instant timestamp = Instant.parse("2026-04-06T10:15:30Z");

        Experiment archived = experiment(ExperimentState.ARCHIVED, startedAt, completedAt);

        Experiment initialized = experimentTimestampPolicy.initializeTimestamps(archived, timestamp);

        assertThat(initialized.startedAt()).isEqualTo(startedAt);
        assertThat(initialized.completedAt()).isEqualTo(completedAt);
    }

    @Test
    void applyTransitionTimestamps_shouldSetStartedAtWhenApprovedTransitionsToRunning() {
        Instant timestamp = Instant.parse("2026-04-06T10:15:30Z");
        Experiment current = experiment(ExperimentState.APPROVED, null, null);
        Experiment transitioned = experiment(ExperimentState.RUNNING, null, null);

        Experiment updated = experimentTimestampPolicy.applyTransitionTimestamps(current, transitioned, timestamp);

        assertThat(updated.startedAt()).isEqualTo(timestamp);
        assertThat(updated.completedAt()).isNull();
    }

    @Test
    void applyTransitionTimestamps_shouldSetCompletedAtWhenExperimentCompletes() {
        Instant startedAt = Instant.parse("2026-04-06T09:00:00Z");
        Instant timestamp = Instant.parse("2026-04-06T10:15:30Z");
        Experiment current = experiment(ExperimentState.RUNNING, startedAt, null);
        Experiment transitioned = experiment(ExperimentState.COMPLETED, startedAt, null);

        Experiment updated = experimentTimestampPolicy.applyTransitionTimestamps(current, transitioned, timestamp);

        assertThat(updated.startedAt()).isEqualTo(startedAt);
        assertThat(updated.completedAt()).isEqualTo(timestamp);
    }

    @Test
    void applyTransitionTimestamps_shouldKeepExistingStartedAtAndCompletedAt() {
        Instant startedAt = Instant.parse("2026-04-06T09:00:00Z");
        Instant completedAt = Instant.parse("2026-04-06T09:30:00Z");
        Instant timestamp = Instant.parse("2026-04-06T10:15:30Z");
        Experiment current = experiment(ExperimentState.APPROVED, startedAt, completedAt);
        Experiment transitioned = experiment(ExperimentState.RUNNING, startedAt, completedAt);

        Experiment updated = experimentTimestampPolicy.applyTransitionTimestamps(current, transitioned, timestamp);

        assertThat(updated.startedAt()).isEqualTo(startedAt);
        assertThat(updated.completedAt()).isEqualTo(completedAt);
    }

    @Test
    void applyTransitionTimestamps_shouldLeaveExperimentUnchangedWhenNoTimestampRulesMatch() {
        Instant timestamp = Instant.parse("2026-04-06T10:15:30Z");
        Experiment current = experiment(ExperimentState.DRAFT, null, null);
        Experiment transitioned = experiment(ExperimentState.RUNNING, null, null);

        Experiment updated = experimentTimestampPolicy.applyTransitionTimestamps(current, transitioned, timestamp);

        assertThat(updated).isEqualTo(transitioned);
    }

    @Test
    void applyTransitionTimestamps_shouldLeaveApprovedToPausedTransitionUnchanged() {
        Instant startedAt = Instant.parse("2026-04-06T09:00:00Z");
        Instant timestamp = Instant.parse("2026-04-06T10:15:30Z");
        Experiment current = experiment(ExperimentState.APPROVED, startedAt, null);
        Experiment transitioned = experiment(ExperimentState.PAUSED, startedAt, null);

        Experiment updated = experimentTimestampPolicy.applyTransitionTimestamps(current, transitioned, timestamp);

        assertThat(updated).isEqualTo(transitioned);
    }

    private Experiment experiment(ExperimentState state, Instant startedAt, Instant completedAt) {
        return new Experiment(
                UUID.randomUUID(),
                "flag-a",
                "CHECKOUT",
                ExperimentRolloutPlan.initial(),
                List.of(),
                state,
                0L,
                startedAt,
                completedAt);
    }
}
