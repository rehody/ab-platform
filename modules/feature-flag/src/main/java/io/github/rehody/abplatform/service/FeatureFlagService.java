package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.dto.request.FeatureFlagCreateRequest;
import io.github.rehody.abplatform.dto.request.FeatureFlagUpdateRequest;
import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.exception.FeatureFlagAlreadyExistsException;
import io.github.rehody.abplatform.exception.FeatureFlagNotFoundException;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.repository.FeatureFlagRepository;
import io.github.rehody.abplatform.util.RedissonLockUtils;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final RedissonClient redissonClient;

    @Transactional
    public FeatureFlagResponse create(FeatureFlagCreateRequest request) {
        return RedissonLockUtils.withLock(redissonClient, request.key(), () -> {
            if (featureFlagRepository.existsByKey(request.key())) {
                throw new FeatureFlagAlreadyExistsException(
                        "Feature flag '%s' already exists".formatted(request.key()));
            }

            FeatureFlag featureFlag = buildFeatureFlag(request.key(), request.defaultValue());
            featureFlagRepository.save(featureFlag);

            return FeatureFlagResponse.from(featureFlag);
        });
    }

    private FeatureFlag getByKeyOrThrow(String key) {
        return featureFlagRepository
                .findByKey(key)
                .orElseThrow(() -> new FeatureFlagNotFoundException("Feature flag '%s' not found".formatted(key)));
    }

    private FeatureFlag buildFeatureFlag(String key, FeatureValue defaultValue) {
        return new FeatureFlag(UUID.randomUUID(), key, defaultValue);
    }

    @Transactional
    public FeatureFlagResponse update(String key, FeatureFlagUpdateRequest request) {
        featureFlagRepository.update(key, request.defaultValue());

        FeatureFlag featureFlag = getByKeyOrThrow(key);

        return FeatureFlagResponse.from(featureFlag);
    }

    @Transactional(readOnly = true)
    public FeatureFlagResponse getByKey(String key) {
        FeatureFlag featureFlag = getByKeyOrThrow(key);
        return FeatureFlagResponse.from(featureFlag);
    }
}
