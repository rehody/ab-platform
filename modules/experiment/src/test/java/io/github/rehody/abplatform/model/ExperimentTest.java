package io.github.rehody.abplatform.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.exception.ExperimentStateTransitionException;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentTest {

    @Test
    void submitForReview_shouldTransitionDraftToInReview() {
        Experiment experiment = experiment(ExperimentState.DRAFT);
        Experiment updated = experiment.submitForReview();

        assertThat(updated.state()).isEqualTo(ExperimentState.IN_REVIEW);
        assertThat(updated.id()).isEqualTo(experiment.id());
        assertThat(updated.flagKey()).isEqualTo(experiment.flagKey());
        assertThat(updated.variants()).isEqualTo(experiment.variants());
        assertThat(updated.version()).isEqualTo(experiment.version());
    }

    @Test
    void approve_shouldTransitionInReviewToApproved() {
        Experiment updated = experiment(ExperimentState.IN_REVIEW).approve();

        assertThat(updated.state()).isEqualTo(ExperimentState.APPROVED);
    }

    @Test
    void reject_shouldTransitionInReviewToRejected() {
        Experiment updated = experiment(ExperimentState.IN_REVIEW).reject();

        assertThat(updated.state()).isEqualTo(ExperimentState.REJECTED);
    }

    @Test
    void returnToDraft_shouldTransitionRejectedToDraft() {
        Experiment updated = experiment(ExperimentState.REJECTED).returnToDraft();

        assertThat(updated.state()).isEqualTo(ExperimentState.DRAFT);
    }

    @Test
    void start_shouldTransitionApprovedToRunning() {
        Experiment updated = experiment(ExperimentState.APPROVED).start();

        assertThat(updated.state()).isEqualTo(ExperimentState.RUNNING);
    }

    @Test
    void pause_shouldTransitionRunningToPaused() {
        Experiment updated = experiment(ExperimentState.RUNNING).pause();

        assertThat(updated.state()).isEqualTo(ExperimentState.PAUSED);
    }

    @Test
    void resume_shouldTransitionPausedToRunning() {
        Experiment updated = experiment(ExperimentState.PAUSED).resume();

        assertThat(updated.state()).isEqualTo(ExperimentState.RUNNING);
    }

    @Test
    void complete_shouldTransitionPausedToCompleted() {
        Experiment updated = experiment(ExperimentState.PAUSED).complete();

        assertThat(updated.state()).isEqualTo(ExperimentState.COMPLETED);
    }

    @Test
    void archive_shouldTransitionCompletedToArchived() {
        Experiment updated = experiment(ExperimentState.COMPLETED).archive();

        assertThat(updated.state()).isEqualTo(ExperimentState.ARCHIVED);
    }

    @Test
    void transition_shouldThrowWhenCurrentStateIsInvalid() {
        assertThatThrownBy(() -> experiment(ExperimentState.DRAFT).approve())
                .isInstanceOf(ExperimentStateTransitionException.class)
                .hasMessageContaining("Cannot approve experiment in state DRAFT");
    }

    private Experiment experiment(ExperimentState state) {
        return new Experiment(
                UUID.randomUUID(),
                "flag-a",
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE)),
                state,
                3L,
                null,
                null);
    }
}
