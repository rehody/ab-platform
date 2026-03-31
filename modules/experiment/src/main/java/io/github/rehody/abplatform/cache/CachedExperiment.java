package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CachedExperiment(
        UUID id,
        String flagKey,
        List<ExperimentVariant> variants,
        ExperimentState state,
        long version,
        Instant startedAt,
        Instant completedAt) {

    public CachedExperiment {
        variants = List.copyOf(variants);
    }

    public static CachedExperiment from(Experiment experiment) {
        return new CachedExperiment(
                experiment.id(),
                experiment.flagKey(),
                experiment.variants(),
                experiment.state(),
                experiment.version(),
                experiment.startedAt(),
                experiment.completedAt());
    }

    public Experiment toModel() {
        return new Experiment(id, flagKey, variants, state, version, startedAt, completedAt);
    }
}
