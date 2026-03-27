package io.github.rehody.abplatform.repository.mapper;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExperimentAggregateMapper {

    public Experiment withVariants(Experiment experiment, List<ExperimentVariant> variants) {
        return new Experiment(
                experiment.id(),
                experiment.flagKey(),
                mapToFeatureValues(variants),
                experiment.state(),
                experiment.version());
    }

    private List<FeatureValue> mapToFeatureValues(List<ExperimentVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return List.of();
        }

        return variants.stream().map(ExperimentVariant::value).toList();
    }
}
