package io.github.rehody.abplatform.service;

import static io.github.rehody.abplatform.support.AssignmentFixtures.boolValue;
import static io.github.rehody.abplatform.support.AssignmentFixtures.experiment;
import static io.github.rehody.abplatform.support.AssignmentFixtures.runningExperiment;
import static io.github.rehody.abplatform.support.AssignmentFixtures.stringValue;
import static io.github.rehody.abplatform.support.AssignmentFixtures.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
import io.github.rehody.abplatform.repository.AssignmentEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private ExperimentService experimentService;

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private ExperimentVariantResolver experimentVariantResolver;

    @Mock
    private ExperimentAssignmentPolicy experimentAssignmentPolicy;

    @Mock
    private AssignmentEventRepository assignmentEventRepository;

    private AssignmentService assignmentService;

    @BeforeEach
    void setUp() {
        assignmentService = new AssignmentService(
                experimentService,
                featureFlagService,
                experimentVariantResolver,
                experimentAssignmentPolicy,
                assignmentEventRepository);
    }

    @Test
    void resolve_shouldReturnDefaultFlagValueWhenExperimentMissing() {
        UUID userId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        when(experimentService.findByFlagKey("flag-a")).thenReturn(Optional.empty());
        when(featureFlagService.getByKey("flag-a"))
                .thenReturn(new FeatureFlag(UUID.randomUUID(), "flag-a", defaultValue, 3L));

        FeatureValue response = assignmentService.resolve(userId, "flag-a");

        assertThat(response).isEqualTo(defaultValue);
        verify(featureFlagService).getByKey("flag-a");
        verify(experimentAssignmentPolicy, never()).canResolveAssignment(any());
        verify(experimentVariantResolver, never()).resolve(any(), any());
    }

    @Test
    void resolve_shouldReturnDefaultFlagValueWhenExperimentCannotBeResolvedForAssignment() {
        UUID userId = UUID.randomUUID();
        Experiment experiment =
                experiment("flag-b", List.of(variant(0, "control", "blue", 1)), ExperimentState.PAUSED, 2L);
        FeatureValue defaultValue = stringValue("gray");
        when(experimentService.findByFlagKey("flag-b")).thenReturn(Optional.of(experiment));
        when(experimentAssignmentPolicy.canResolveAssignment(experiment)).thenReturn(false);
        when(featureFlagService.getByKey("flag-b"))
                .thenReturn(new FeatureFlag(UUID.randomUUID(), "flag-b", defaultValue, 1L));

        FeatureValue response = assignmentService.resolve(userId, "flag-b");

        assertThat(response).isEqualTo(defaultValue);
        verify(experimentAssignmentPolicy).canResolveAssignment(experiment);
        verify(experimentVariantResolver, never()).resolve(any(), any());
    }

    @Test
    void resolve_shouldReturnDefaultFlagValueWhenControlVariantIsSelected() {
        UUID userId = UUID.randomUUID();
        FeatureValue defaultValue = stringValue("green");
        Experiment experiment = runningExperiment(
                "flag-c", List.of(variant(0, "control", "green", 1), variant(1, "treatment", "red", 2)), 5L);
        ExperimentVariant controlVariant = variant(0, "control", "green", 1);
        when(experimentService.findByFlagKey("flag-c")).thenReturn(Optional.of(experiment));
        when(experimentAssignmentPolicy.canResolveAssignment(experiment)).thenReturn(true);
        when(experimentVariantResolver.resolve(experiment, userId)).thenReturn(controlVariant);
        when(featureFlagService.getByKey("flag-c"))
                .thenReturn(new FeatureFlag(UUID.randomUUID(), "flag-c", defaultValue, 5L));

        FeatureValue response = assignmentService.resolve(userId, "flag-c");

        assertThat(response).isEqualTo(defaultValue);
        verify(experimentVariantResolver).resolve(experiment, userId);
        verify(featureFlagService).getByKey("flag-c");
        verify(assignmentEventRepository).saveIfAbsent(eq(experiment.id()), eq(controlVariant.id()), eq(userId), any());
    }

    @Test
    void resolve_shouldReturnResolvedVariantValueWhenRegularVariantIsSelected() {
        UUID userId = UUID.randomUUID();
        Experiment experiment = runningExperiment(
                "flag-d", List.of(variant(0, "control", "green", 1), variant(1, "treatment", "red", 2)), 5L);
        ExperimentVariant variant = variant(1, "treatment", "red", 2);
        when(experimentService.findByFlagKey("flag-d")).thenReturn(Optional.of(experiment));
        when(experimentAssignmentPolicy.canResolveAssignment(experiment)).thenReturn(true);
        when(experimentVariantResolver.resolve(experiment, userId)).thenReturn(variant);

        FeatureValue response = assignmentService.resolve(userId, "flag-d");

        assertThat(response).isEqualTo(variant.value());
        verify(experimentVariantResolver).resolve(experiment, userId);
        verify(featureFlagService, never()).getByKey(anyString());
        verify(assignmentEventRepository).saveIfAbsent(eq(experiment.id()), eq(variant.id()), eq(userId), any());
    }
}
