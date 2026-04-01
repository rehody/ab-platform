package io.github.rehody.abplatform.policy;

import io.github.rehody.abplatform.repository.ExperimentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentFeatureFlagUpdatePolicy implements FeatureFlagUpdatePolicy {

    private final ExperimentRepository experimentRepository;

    @Override
    public boolean canUpdateDefaultValue(String flagKey) {
        return !experimentRepository.existsByFlagKey(flagKey);
    }
}
