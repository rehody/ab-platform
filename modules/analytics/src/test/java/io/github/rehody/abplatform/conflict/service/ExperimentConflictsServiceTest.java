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

    private Experiment experiment(UUID id, String flagKey, String domainKey, ExperimentState state) {
        return new Experiment(id, flagKey, domainKey, List.of(), state, 0L, null, null);
    }
}
