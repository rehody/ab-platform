package io.github.rehody.abplatform.conflict.policy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.conflict.enums.ExperimentConflictType;
import io.github.rehody.abplatform.conflict.model.ExperimentConflict;
import io.github.rehody.abplatform.conflict.service.ExperimentConflictsService;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.exception.ExperimentBlockingConflictException;
import io.github.rehody.abplatform.model.Experiment;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentConflictActivationPolicyTest {

    @Mock
    private ExperimentConflictsService experimentConflictsService;

    @Test
    void validateActivation_shouldUseConflictsServiceAndThrowStructuredBlockingConflict() {
        Experiment experiment = new Experiment(
                UUID.randomUUID(), "flag-a", "CHECKOUT", List.of(), ExperimentState.APPROVED, 0L, null, null);
        ExperimentConflict blockingConflict = new ExperimentConflict(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                ExperimentState.RUNNING,
                "flag-b",
                "PRICING",
                List.of(ExperimentConflictType.SAME_FLAG, ExperimentConflictType.DOMAIN_OVERLAP),
                ConflictSeverity.BLOCKING);
        ExperimentConflictActivationPolicy policy = new ExperimentConflictActivationPolicy(experimentConflictsService);

        when(experimentConflictsService.getBlockingConflicts(experiment)).thenReturn(List.of(blockingConflict));

        assertThatThrownBy(() -> policy.validateActivation(experiment))
                .isInstanceOfSatisfying(ExperimentBlockingConflictException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.conflicts())
                            .hasSize(1);
                    org.assertj.core.api.Assertions.assertThat(
                                    exception.conflicts().getFirst().experimentId())
                            .isEqualTo(blockingConflict.experimentId());
                    org.assertj.core.api.Assertions.assertThat(
                                    exception.conflicts().getFirst().state())
                            .isEqualTo(blockingConflict.state());
                    org.assertj.core.api.Assertions.assertThat(
                                    exception.conflicts().getFirst().flagKey())
                            .isEqualTo(blockingConflict.flagKey());
                    org.assertj.core.api.Assertions.assertThat(
                                    exception.conflicts().getFirst().domainKey())
                            .isEqualTo(blockingConflict.domainKey());
                    org.assertj.core.api.Assertions.assertThat(
                                    exception.conflicts().getFirst().conflictTypes())
                            .containsExactly("SAME_FLAG", "DOMAIN_OVERLAP");
                    org.assertj.core.api.Assertions.assertThat(
                                    exception.conflicts().getFirst().severity())
                            .isEqualTo("BLOCKING");
                });
    }

    @Test
    void validateActivation_shouldReturnWhenBlockingConflictsAbsent() {
        Experiment experiment = new Experiment(
                UUID.randomUUID(), "flag-a", "CHECKOUT", List.of(), ExperimentState.APPROVED, 0L, null, null);
        ExperimentConflictActivationPolicy policy = new ExperimentConflictActivationPolicy(experimentConflictsService);

        when(experimentConflictsService.getBlockingConflicts(experiment)).thenReturn(List.of());

        policy.validateActivation(experiment);
    }
}
