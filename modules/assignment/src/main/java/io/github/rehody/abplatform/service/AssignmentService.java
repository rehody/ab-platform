package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.dto.request.AssignmentRequest;
import io.github.rehody.abplatform.dto.response.AssignmentResponse;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final ExperimentService experimentService;
    private final FeatureFlagService featureFlagService;
    private final ExperimentVariantResolver experimentVariantResolver;
    private final ExperimentAssignmentPolicy experimentAssignmentPolicy;

    public AssignmentResponse resolve(AssignmentRequest request) {
        String flagKey = request.flagKey();
        Optional<Experiment> experimentOptional = experimentService.findByFlagKey(flagKey);

        if (experimentOptional.isEmpty()) {
            return defaultAssignment(flagKey);
        }

        Experiment experiment = experimentOptional.get();
        if (!experimentAssignmentPolicy.canResolveAssignment(experiment)) {
            return defaultAssignment(flagKey);
        }

        FeatureValue value =
                experimentVariantResolver.resolve(experiment, request.userId()).value();
        return AssignmentResponse.of(value);
    }

    private AssignmentResponse defaultAssignment(String flagKey) {
        FeatureValue defaultValue = featureFlagService.getByKey(flagKey).defaultValue();
        return AssignmentResponse.of(defaultValue);
    }
}
