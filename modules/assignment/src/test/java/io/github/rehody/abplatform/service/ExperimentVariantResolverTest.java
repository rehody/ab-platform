package io.github.rehody.abplatform.service;

import static io.github.rehody.abplatform.support.AssignmentFixtures.runningExperiment;
import static io.github.rehody.abplatform.support.AssignmentFixtures.variant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.service.bucket.UserExperimentBucketResolver;
import io.github.rehody.abplatform.service.snapshot.BucketRange;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshot;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshotReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentVariantResolverTest {

    @Mock
    private VariantAllocationSnapshotReader variantAllocationSnapshotReader;

    @Mock
    private UserExperimentBucketResolver userExperimentBucketResolver;

    private ExperimentVariantResolver experimentVariantResolver;

    @BeforeEach
    void setUp() {
        experimentVariantResolver =
                new ExperimentVariantResolver(variantAllocationSnapshotReader, userExperimentBucketResolver);
    }

    @Test
    void resolve_shouldThrowWhenResolvableExperimentHasNoVariants() {
        Experiment experiment = runningExperiment("flag-a", "CHECKOUT", List.of(), 1L);

        assertThatThrownBy(() -> experimentVariantResolver.resolve(experiment, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Resolvable experiment %s must have at least one variant".formatted(experiment.id()));
    }

    @Test
    void resolve_shouldReturnVariantSelectedFromSnapshotForResolvedBucket() {
        UUID userId = UUID.randomUUID();
        ExperimentVariant control = variant(0, "control", "blue", 1);
        ExperimentVariant treatment = variant(1, "treatment", "red", 1);
        Experiment experiment = runningExperiment("flag-b", "CHECKOUT", List.of(control, treatment), 4L);
        VariantAllocationSnapshot snapshot = new VariantAllocationSnapshot(
                List.of(new BucketRange(0, 5000, control), new BucketRange(5000, 10000, treatment)));
        when(variantAllocationSnapshotReader.get(experiment)).thenReturn(snapshot);
        when(userExperimentBucketResolver.resolve(experiment.id(), userId)).thenReturn(7777);

        ExperimentVariant resolved = experimentVariantResolver.resolve(experiment, userId);

        assertThat(resolved).isEqualTo(treatment);
        verify(variantAllocationSnapshotReader).get(experiment);
        verify(userExperimentBucketResolver).resolve(experiment.id(), userId);
    }
}
