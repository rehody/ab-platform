package io.github.rehody.abplatform.service.snapshot;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AssignmentVariantsPreparer {

    public List<ExperimentVariant> prepare(Experiment experiment) {
        List<ExperimentVariant> variants = experiment.variants();
        if (variants.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot prepare assignment variants for experiment %s without variants".formatted(experiment.id()));
        }

        return variants.stream()
                .sorted(Comparator.comparingInt(ExperimentVariant::position))
                .toList();
    }
}
