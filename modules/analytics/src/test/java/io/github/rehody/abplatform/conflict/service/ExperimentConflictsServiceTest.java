package io.github.rehody.abplatform.conflict.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.conflict.model.ExperimentConflict;
import io.github.rehody.abplatform.conflict.repository.ExperimentConflictRepository;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.service.ExperimentService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentConflictsServiceTest {

    @Mock
    private ExperimentConflictRepository experimentConflictRepository;

    @Mock
    private ExperimentService experimentService;

    private ExperimentConflictsService experimentConflictsService;

    @BeforeEach
    void setUp() {
        experimentConflictsService = new ExperimentConflictsService(
                experimentConflictRepository, experimentService, new ExperimentConflictSeverityResolver());
    }

    @Test
    void getAll_shouldMapRunningToBlockingAndActiveNonRunningStatesToWarning() {
        UUID experimentId = UUID.randomUUID();
        Experiment experiment = experiment(experimentId, "flag-a", "CHECKOUT", ExperimentState.APPROVED);
        Experiment runningConflict = experiment(UUID.randomUUID(), "flag-a", "PRICING", ExperimentState.RUNNING);
        Experiment draftConflict = experiment(UUID.randomUUID(), "flag-b", "CHECKOUT", ExperimentState.DRAFT);
        Experiment rejectedConflict = experiment(UUID.randomUUID(), "flag-c", "CHECKOUT", ExperimentState.REJECTED);
        Experiment inReviewConflict = experiment(UUID.randomUUID(), "flag-d", "CHECKOUT", ExperimentState.IN_REVIEW);
        Experiment approvedConflict = experiment(UUID.randomUUID(), "flag-e", "CHECKOUT", ExperimentState.APPROVED);
        Experiment pausedConflict = experiment(UUID.randomUUID(), "flag-f", "CHECKOUT", ExperimentState.PAUSED);

        when(experimentService.getById(experimentId)).thenReturn(experiment);
        when(experimentConflictRepository.findAll(experimentId, "flag-a", "CHECKOUT"))
                .thenReturn(List.of(
                        runningConflict,
                        draftConflict,
                        rejectedConflict,
                        inReviewConflict,
                        approvedConflict,
                        pausedConflict));

        List<ExperimentConflict> conflicts = experimentConflictsService.getAll(experimentId);

        assertThat(conflicts).hasSize(6);
        assertThat(conflicts.getFirst().severity()).isEqualTo(ConflictSeverity.BLOCKING);
        assertThat(conflicts.subList(1, conflicts.size()))
                .allMatch(conflict -> conflict.severity().isWarning());
    }

    @Test
    void getAll_shouldExcludeCompletedAndArchivedConflicts() {
        UUID experimentId = UUID.randomUUID();
        Experiment experiment = experiment(experimentId, "flag-a", "CHECKOUT", ExperimentState.DRAFT);
        Experiment completedConflict = experiment(UUID.randomUUID(), "flag-a", "PRICING", ExperimentState.COMPLETED);
        Experiment archivedConflict = experiment(UUID.randomUUID(), "flag-b", "CHECKOUT", ExperimentState.ARCHIVED);

        when(experimentService.getById(experimentId)).thenReturn(experiment);
        when(experimentConflictRepository.findAll(experimentId, "flag-a", "CHECKOUT"))
                .thenReturn(List.of(completedConflict, archivedConflict));

        List<ExperimentConflict> conflicts = experimentConflictsService.getAll(experimentId);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void getAll_shouldReturnWarningForBothSidesOfDraftConflict() {
        UUID firstExperimentId = UUID.randomUUID();
        UUID secondExperimentId = UUID.randomUUID();
        Experiment firstExperiment = experiment(firstExperimentId, "flag-a", "CHECKOUT", ExperimentState.DRAFT);
        Experiment secondExperiment = experiment(secondExperimentId, "flag-a", "PRICING", ExperimentState.DRAFT);

        when(experimentService.getById(firstExperimentId)).thenReturn(firstExperiment);
        when(experimentService.getById(secondExperimentId)).thenReturn(secondExperiment);
        when(experimentConflictRepository.findAll(firstExperimentId, "flag-a", "CHECKOUT"))
                .thenReturn(List.of(secondExperiment));
        when(experimentConflictRepository.findAll(secondExperimentId, "flag-a", "PRICING"))
                .thenReturn(List.of(firstExperiment));

        List<ExperimentConflict> firstConflicts = experimentConflictsService.getAll(firstExperimentId);
        List<ExperimentConflict> secondConflicts = experimentConflictsService.getAll(secondExperimentId);

        assertThat(firstConflicts).hasSize(1);
        assertThat(firstConflicts.getFirst().experimentId()).isEqualTo(secondExperimentId);
        assertThat(firstConflicts.getFirst().severity()).isEqualTo(ConflictSeverity.WARNING);

        assertThat(secondConflicts).hasSize(1);
        assertThat(secondConflicts.getFirst().experimentId()).isEqualTo(firstExperimentId);
        assertThat(secondConflicts.getFirst().severity()).isEqualTo(ConflictSeverity.WARNING);
    }

    @Test
    void getAll_shouldRecalculateConflictsWhenDraftIsUpdated() {
        UUID experimentId = UUID.randomUUID();
        Experiment initialDraft = experiment(experimentId, "flag-a", "CHECKOUT", ExperimentState.DRAFT);
        Experiment updatedDraft = experiment(experimentId, "flag-a", "PRICING", ExperimentState.DRAFT);
        Experiment domainConflict = experiment(UUID.randomUUID(), "flag-b", "PRICING", ExperimentState.APPROVED);

        when(experimentService.getById(experimentId)).thenReturn(initialDraft, updatedDraft);
        when(experimentConflictRepository.findAll(experimentId, "flag-a", "CHECKOUT"))
                .thenReturn(List.of());
        when(experimentConflictRepository.findAll(experimentId, "flag-a", "PRICING"))
                .thenReturn(List.of(domainConflict));

        List<ExperimentConflict> initialConflicts = experimentConflictsService.getAll(experimentId);
        List<ExperimentConflict> updatedConflicts = experimentConflictsService.getAll(experimentId);

        assertThat(initialConflicts).isEmpty();
        assertThat(updatedConflicts).hasSize(1);
        assertThat(updatedConflicts.getFirst().experimentId()).isEqualTo(domainConflict.id());
        assertThat(updatedConflicts.getFirst().severity()).isEqualTo(ConflictSeverity.WARNING);
    }

    @Test
    void getAll_shouldRecalculateConflictsWhenDraftFlagKeyChanges() {
        UUID experimentId = UUID.randomUUID();
        Experiment initialDraft = experiment(experimentId, "flag-a", "CHECKOUT", ExperimentState.DRAFT);
        Experiment updatedDraft = experiment(experimentId, "flag-b", "CHECKOUT", ExperimentState.DRAFT);
        Experiment flagConflict = experiment(UUID.randomUUID(), "flag-b", "PRICING", ExperimentState.APPROVED);

        when(experimentService.getById(experimentId)).thenReturn(initialDraft, updatedDraft);
        when(experimentConflictRepository.findAll(experimentId, "flag-a", "CHECKOUT"))
                .thenReturn(List.of());
        when(experimentConflictRepository.findAll(experimentId, "flag-b", "CHECKOUT"))
                .thenReturn(List.of(flagConflict));

        List<ExperimentConflict> initialConflicts = experimentConflictsService.getAll(experimentId);
        List<ExperimentConflict> updatedConflicts = experimentConflictsService.getAll(experimentId);

        assertThat(initialConflicts).isEmpty();
        assertThat(updatedConflicts).hasSize(1);
        assertThat(updatedConflicts.getFirst().experimentId()).isEqualTo(flagConflict.id());
        assertThat(updatedConflicts.getFirst().severity()).isEqualTo(ConflictSeverity.WARNING);
    }

    @Test
    void getAll_shouldRecalculateConflictsWhenDraftDomainKeyChanges() {
        UUID experimentId = UUID.randomUUID();
        Experiment initialDraft = experiment(experimentId, "flag-a", "CHECKOUT", ExperimentState.DRAFT);
        Experiment updatedDraft = experiment(experimentId, "flag-a", "PRICING", ExperimentState.DRAFT);
        Experiment domainConflict = experiment(UUID.randomUUID(), "flag-c", "PRICING", ExperimentState.PAUSED);

        when(experimentService.getById(experimentId)).thenReturn(initialDraft, updatedDraft);
        when(experimentConflictRepository.findAll(experimentId, "flag-a", "CHECKOUT"))
                .thenReturn(List.of());
        when(experimentConflictRepository.findAll(experimentId, "flag-a", "PRICING"))
                .thenReturn(List.of(domainConflict));

        List<ExperimentConflict> initialConflicts = experimentConflictsService.getAll(experimentId);
        List<ExperimentConflict> updatedConflicts = experimentConflictsService.getAll(experimentId);

        assertThat(initialConflicts).isEmpty();
        assertThat(updatedConflicts).hasSize(1);
        assertThat(updatedConflicts.getFirst().experimentId()).isEqualTo(domainConflict.id());
        assertThat(updatedConflicts.getFirst().severity()).isEqualTo(ConflictSeverity.WARNING);
    }

    @Test
    void getAll_shouldRecalculateConflictsAfterConflictingExperimentCompletes() {
        UUID experimentId = UUID.randomUUID();
        Experiment experiment = experiment(experimentId, "flag-a", "CHECKOUT", ExperimentState.APPROVED);
        Experiment activeConflict = experiment(UUID.randomUUID(), "flag-a", "PRICING", ExperimentState.APPROVED);
        Experiment completedConflict = experiment(activeConflict.id(), "flag-a", "PRICING", ExperimentState.COMPLETED);

        when(experimentService.getById(experimentId)).thenReturn(experiment, experiment);
        when(experimentConflictRepository.findAll(experimentId, "flag-a", "CHECKOUT"))
                .thenReturn(List.of(activeConflict), List.of(completedConflict));

        List<ExperimentConflict> conflictsBeforeComplete = experimentConflictsService.getAll(experimentId);
        List<ExperimentConflict> conflictsAfterComplete = experimentConflictsService.getAll(experimentId);

        assertThat(conflictsBeforeComplete).hasSize(1);
        assertThat(conflictsBeforeComplete.getFirst().severity()).isEqualTo(ConflictSeverity.WARNING);
        assertThat(conflictsAfterComplete).isEmpty();
    }

    @Test
    void getAll_shouldRecalculateConflictsAfterConflictingExperimentIsArchived() {
        UUID experimentId = UUID.randomUUID();
        Experiment experiment = experiment(experimentId, "flag-a", "CHECKOUT", ExperimentState.APPROVED);
        Experiment activeConflict = experiment(UUID.randomUUID(), "flag-a", "PRICING", ExperimentState.PAUSED);
        Experiment archivedConflict = experiment(activeConflict.id(), "flag-a", "PRICING", ExperimentState.ARCHIVED);

        when(experimentService.getById(experimentId)).thenReturn(experiment, experiment);
        when(experimentConflictRepository.findAll(experimentId, "flag-a", "CHECKOUT"))
                .thenReturn(List.of(activeConflict), List.of(archivedConflict));

        List<ExperimentConflict> conflictsBeforeArchive = experimentConflictsService.getAll(experimentId);
        List<ExperimentConflict> conflictsAfterArchive = experimentConflictsService.getAll(experimentId);

        assertThat(conflictsBeforeArchive).hasSize(1);
        assertThat(conflictsBeforeArchive.getFirst().severity()).isEqualTo(ConflictSeverity.WARNING);
        assertThat(conflictsAfterArchive).isEmpty();
    }

    @Test
    void getBlockingConflicts_shouldReturnOnlyRunningConflicts() {
        Experiment experiment = experiment(UUID.randomUUID(), "flag-a", "CHECKOUT", ExperimentState.PAUSED);
        Experiment runningConflict = experiment(UUID.randomUUID(), "flag-a", "PRICING", ExperimentState.RUNNING);
        Experiment warningConflict = experiment(UUID.randomUUID(), "flag-b", "CHECKOUT", ExperimentState.APPROVED);

        when(experimentConflictRepository.findAll(experiment.id(), "flag-a", "CHECKOUT"))
                .thenReturn(List.of(runningConflict, warningConflict));

        List<ExperimentConflict> conflicts = experimentConflictsService.getBlockingConflicts(experiment);

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().experimentId()).isEqualTo(runningConflict.id());
        assertThat(conflicts.getFirst().severity()).isEqualTo(ConflictSeverity.BLOCKING);
    }

    @Test
    void getBlockingConflicts_shouldReturnEmptyForCompletedAndArchivedExperiments() {
        Experiment completedExperiment = experiment(UUID.randomUUID(), "flag-a", "CHECKOUT", ExperimentState.COMPLETED);
        Experiment archivedExperiment = experiment(UUID.randomUUID(), "flag-a", "CHECKOUT", ExperimentState.ARCHIVED);

        assertThat(experimentConflictsService.getBlockingConflicts(completedExperiment))
                .isEmpty();
        assertThat(experimentConflictsService.getBlockingConflicts(archivedExperiment))
                .isEmpty();
    }

    @Test
    void getAll_shouldReturnEmptyForConflictIrrelevantExperimentAndUnmatchedRepositoryRows() {
        Experiment completedExperiment = experiment(UUID.randomUUID(), "flag-a", "CHECKOUT", ExperimentState.COMPLETED);
        Experiment unrelatedExperiment = experiment(UUID.randomUUID(), "flag-b", "PRICING", ExperimentState.RUNNING);
        Experiment activeExperiment = experiment(UUID.randomUUID(), "flag-a", "CHECKOUT", ExperimentState.DRAFT);

        assertThat(experimentConflictsService.getAll(completedExperiment)).isEmpty();

        when(experimentConflictRepository.findAll(activeExperiment.id(), "flag-a", "CHECKOUT"))
                .thenReturn(List.of(unrelatedExperiment));

        assertThat(experimentConflictsService.getAll(activeExperiment)).isEmpty();
    }

    private Experiment experiment(UUID id, String flagKey, String domainKey, ExperimentState state) {
        return new Experiment(id, flagKey, domainKey, List.of(), state, 0L, null, null);
    }
}
