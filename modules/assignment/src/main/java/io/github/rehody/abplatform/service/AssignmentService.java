package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.dto.request.AssignmentRequest;
import io.github.rehody.abplatform.dto.response.AssignmentResponse;
import io.github.rehody.abplatform.model.Experiment;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final ExperimentService experimentService;
    private final FeatureFlagService featureFlagService;
    private final ExperimentVariantResolver experimentVariantResolver;

    public AssignmentResponse resolve(AssignmentRequest request) {
        String flagKey = request.flagKey();
        Optional<Experiment> experimentOptional = experimentService.findByFlagKey(flagKey);

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
        return AssignmentResponse.of(featureFlagService.getByKey(flagKey).defaultValue());
    }
}
