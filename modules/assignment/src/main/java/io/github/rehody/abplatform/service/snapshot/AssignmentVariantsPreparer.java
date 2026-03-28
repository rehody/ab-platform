package io.github.rehody.abplatform.service.snapshot;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.service.validation.ExperimentVariantAssignmentValidator;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssignmentVariantsPreparer {

    private final ExperimentVariantAssignmentValidator experimentVariantAssignmentValidator;

    public List<ExperimentVariant> prepare(Experiment experiment) {
        List<ExperimentVariant> variants = experiment.variants();
        if (variants.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot prepare assignment variants for experiment %s without variants".formatted(experiment.id()));
        }

        experimentVariantAssignmentValidator.validate(experiment.id(), variants);

        return variants.stream()
                .sorted(Comparator.comparingInt(ExperimentVariant::position))
                .toList();
    }
}
