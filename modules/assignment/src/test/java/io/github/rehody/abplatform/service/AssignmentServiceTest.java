package io.github.rehody.abplatform.service;

import static io.github.rehody.abplatform.support.AssignmentFixtures.boolValue;
import static io.github.rehody.abplatform.support.AssignmentFixtures.experiment;
import static io.github.rehody.abplatform.support.AssignmentFixtures.runningExperiment;
import static io.github.rehody.abplatform.support.AssignmentFixtures.stringValue;
import static io.github.rehody.abplatform.support.AssignmentFixtures.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.dto.request.AssignmentRequest;
import io.github.rehody.abplatform.dto.response.AssignmentResponse;
import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
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

    private AssignmentService assignmentService;

    @BeforeEach
    void setUp() {
        assignmentService = new AssignmentService(
                experimentService, featureFlagService, experimentVariantResolver, experimentAssignmentPolicy);
    }

    @Test
    void resolve_shouldReturnDefaultFlagValueWhenExperimentMissing() {
        UUID userId = UUID.randomUUID();
        FeatureValue defaultValue = boolValue(true);
        AssignmentRequest request = new AssignmentRequest(userId, "flag-a");
        when(experimentService.findByFlagKey("flag-a")).thenReturn(Optional.empty());
        when(featureFlagService.getByKey("flag-a")).thenReturn(new FeatureFlagResponse("flag-a", defaultValue, 3L));

        AssignmentResponse response = assignmentService.resolve(request);

        assertThat(response).isEqualTo(AssignmentResponse.of(defaultValue));
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
        AssignmentRequest request = new AssignmentRequest(userId, "flag-b");
        when(experimentService.findByFlagKey("flag-b")).thenReturn(Optional.of(experiment));
        when(experimentAssignmentPolicy.canResolveAssignment(experiment)).thenReturn(false);
        when(featureFlagService.getByKey("flag-b")).thenReturn(new FeatureFlagResponse("flag-b", defaultValue, 1L));

        AssignmentResponse response = assignmentService.resolve(request);

        assertThat(response).isEqualTo(AssignmentResponse.of(defaultValue));
        verify(experimentAssignmentPolicy).canResolveAssignment(experiment);
        verify(experimentVariantResolver, never()).resolve(any(), any());
    }

    @Test
    void resolve_shouldReturnResolvedVariantValueWhenExperimentCanBeResolved() {
        UUID userId = UUID.randomUUID();
        Experiment experiment = runningExperiment("flag-c", List.of(variant(0, "control", "green", 1)), 5L);
        ExperimentVariant variant = variant(0, "treatment", "red", 2);
        AssignmentRequest request = new AssignmentRequest(userId, "flag-c");
        when(experimentService.findByFlagKey("flag-c")).thenReturn(Optional.of(experiment));
        when(experimentAssignmentPolicy.canResolveAssignment(experiment)).thenReturn(true);
        when(experimentVariantResolver.resolve(experiment, userId)).thenReturn(variant);

        AssignmentResponse response = assignmentService.resolve(request);

        assertThat(response).isEqualTo(AssignmentResponse.of(variant.value()));
        verify(experimentVariantResolver).resolve(experiment, userId);
        verify(featureFlagService, never()).getByKey(anyString());
    }
}
