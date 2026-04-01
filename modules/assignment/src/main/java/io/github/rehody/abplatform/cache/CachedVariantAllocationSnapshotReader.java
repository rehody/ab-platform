package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshot;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshotFactory;
import io.github.rehody.abplatform.service.snapshot.VariantAllocationSnapshotReader;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CachedVariantAllocationSnapshotReader implements VariantAllocationSnapshotReader {

    private final AssignmentPlanCache assignmentPlanCache;
    private final VariantAllocationSnapshotFactory variantAllocationSnapshotFactory;

    @Override
    public VariantAllocationSnapshot get(Experiment experiment) {
        return assignmentPlanCache
                .getOrLoad(cacheKey(experiment), () -> Optional.of(variantAllocationSnapshotFactory.create(experiment)))
                .orElseThrow(() -> new IllegalStateException(
                        "Assignment plan snapshot not found for experiment %s".formatted(experiment.id())));
    }

    private String cacheKey(Experiment experiment) {
        return "%s:%d".formatted(experiment.id(), experiment.version());
    }
}
