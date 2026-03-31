package io.github.rehody.abplatform.repository.mapper;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExperimentAggregateMapper {

    public Experiment withVariants(Experiment experiment, List<ExperimentVariant> variants) {
        return new Experiment(
                experiment.id(),
                experiment.flagKey(),
                copyVariants(variants),
                experiment.state(),
                experiment.version(),
                experiment.startedAt(),
                experiment.completedAt());
    }

    private List<ExperimentVariant> copyVariants(List<ExperimentVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return List.of();
        }

        return List.copyOf(variants);
    }
}
