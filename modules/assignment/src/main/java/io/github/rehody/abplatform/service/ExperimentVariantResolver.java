package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.service.bucket.UserExperimentBucketResolver;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshot;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshotReader;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentVariantResolver {

    private final VariantAllocationSnapshotReader variantAllocationSnapshotReader;
    private final UserExperimentBucketResolver userExperimentBucketResolver;

    public Optional<ExperimentVariant> resolve(Experiment experiment, UUID userId) {
        if (experiment.variants().isEmpty()) {
            return Optional.empty();
        }

        VariantAllocationSnapshot snapshot = variantAllocationSnapshotReader.get(experiment);
        int bucket = userExperimentBucketResolver.resolve(experiment.id(), userId);
        ExperimentVariant selectedVariant = snapshot.selectVariant(experiment.id(), bucket);
        return Optional.of(selectedVariant);
    }
}
