package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.service.bucket.UserExperimentBucketResolver;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshot;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshotReader;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentVariantResolver {

    private final VariantAllocationSnapshotReader variantAllocationSnapshotReader;
    private final UserExperimentBucketResolver userExperimentBucketResolver;

    public ExperimentVariant resolve(Experiment experiment, UUID userId) {
        if (experiment.variants().isEmpty()) {
            throw new IllegalStateException(
                    "Resolvable experiment %s must have at least one variant".formatted(experiment.id()));
        }

        VariantAllocationSnapshot snapshot = variantAllocationSnapshotReader.get(experiment);
        int bucket = userExperimentBucketResolver.resolve(experiment.id(), userId);
        return snapshot.selectVariant(experiment.id(), bucket);
    }
}
