package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.dto.request.AssignmentRequest;
import io.github.rehody.abplatform.dto.response.AssignmentResponse;
import io.github.rehody.abplatform.exception.FeatureFlagNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.FeatureFlagRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final ExperimentRepository experimentRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final ExperimentVariantResolver experimentVariantResolver;

    public AssignmentResponse resolve(AssignmentRequest request) {
        String flagKey = request.flagKey();
        Optional<Experiment> experimentOptional = experimentRepository.findByFlagKey(flagKey);

        if (experimentOptional.isEmpty()) {
            return defaultAssignment(flagKey);
        }

        Experiment experiment = experimentOptional.get();
        return experimentVariantResolver
                .resolve(experiment, request.userId())
                .map(variant -> AssignmentResponse.of(variant.value()))
                .orElseGet(() -> defaultAssignment(flagKey));
    }

    private AssignmentResponse defaultAssignment(String flagKey) {
        FeatureFlag featureFlag = findByKeyOrThrow(flagKey);
        return AssignmentResponse.of(featureFlag.defaultValue());
    }

    private FeatureFlag findByKeyOrThrow(String flagKey) {
        return featureFlagRepository
                .findByKey(flagKey)
                .orElseThrow(() -> new FeatureFlagNotFoundException("Feature flag '%s' not found".formatted(flagKey)));
    }
}
