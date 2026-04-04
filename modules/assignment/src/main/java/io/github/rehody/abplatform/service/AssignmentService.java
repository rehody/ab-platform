package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
import io.github.rehody.abplatform.repository.AssignmentEventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final ExperimentService experimentService;
    private final FeatureFlagService featureFlagService;
    private final ExperimentVariantResolver experimentVariantResolver;
    private final ExperimentAssignmentPolicy experimentAssignmentPolicy;
    private final AssignmentEventRepository assignmentEventRepository;

    public FeatureValue resolve(UUID userId, String flagKey) {
        Optional<Experiment> experiment = findResolvableExperiment(flagKey);
        if (experiment.isEmpty()) {
            return defaultAssignment(flagKey);
        }

        ExperimentVariant resolvedVariant = experimentVariantResolver.resolve(experiment.get(), userId);
        recordAssignment(experiment.get(), resolvedVariant, userId);

        if (resolvedVariant.isControl()) {
            return defaultAssignment(flagKey);
        }

        return resolvedVariant.value();
    }

    private FeatureValue defaultAssignment(String flagKey) {
        return featureFlagService.getByKey(flagKey).defaultValue();
    }

    private Optional<Experiment> findResolvableExperiment(String flagKey) {
        return experimentService.findByFlagKey(flagKey).filter(experimentAssignmentPolicy::canResolveAssignment);
    }

    private void recordAssignment(Experiment experiment, ExperimentVariant resolvedVariant, UUID userId) {
        assignmentEventRepository.saveIfAbsent(experiment.id(), resolvedVariant.id(), userId, Instant.now());
    }
}
