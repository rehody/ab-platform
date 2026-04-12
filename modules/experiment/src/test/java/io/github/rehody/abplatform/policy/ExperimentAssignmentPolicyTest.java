package io.github.rehody.abplatform.policy;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentAssignmentPolicyTest {

    @Mock
    private ExperimentVariantPolicy experimentVariantPolicy;

    private ExperimentAssignmentPolicy experimentAssignmentPolicy;

    @BeforeEach
    void setUp() {
        experimentAssignmentPolicy = new ExperimentAssignmentPolicy(experimentVariantPolicy);
    }

    @Test
    void canResolveAssignment_shouldReturnTrueOnlyForRunningExperiments() {
        org.assertj.core.api.Assertions.assertThat(
                        experimentAssignmentPolicy.canResolveAssignment(experiment(ExperimentState.RUNNING)))
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(
                        experimentAssignmentPolicy.canResolveAssignment(experiment(ExperimentState.PAUSED)))
                .isFalse();
    }

    @Test
    void validateAssignmentInvariants_shouldValidateVariantsForRunningExperiments() {
        Experiment experiment = experiment(ExperimentState.RUNNING);

        experimentAssignmentPolicy.validateAssignmentInvariants(experiment);

        verify(experimentVariantPolicy).validateResolvableVariantConfiguration(experiment.id(), experiment.variants());
    }

    @Test
    void validateAssignmentInvariants_shouldSkipValidationForNonRunningExperiments() {
        Experiment experiment = experiment(ExperimentState.APPROVED);

        experimentAssignmentPolicy.validateAssignmentInvariants(experiment);

        verify(experimentVariantPolicy, never())
                .validateResolvableVariantConfiguration(experiment.id(), experiment.variants());
    }

    private Experiment experiment(ExperimentState state) {
        return new Experiment(
                UUID.randomUUID(),
                "flag-a",
                "CHECKOUT",
                ExperimentRolloutPlan.initial(),
                List.of(),
                state,
                0L,
                null,
                null);
    }
}
