package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.cache.FeatureFlagCache;
import io.github.rehody.abplatform.exception.FeatureFlagAlreadyExistsException;
import io.github.rehody.abplatform.exception.FeatureFlagNotFoundException;
import io.github.rehody.abplatform.exception.FeatureFlagUpdateBlockedException;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.policy.FeatureFlagUpdatePolicy;
import io.github.rehody.abplatform.repository.FeatureFlagRepository;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private static final LockNamespace FEATURE_FLAG_LOCK_NAMESPACE = LockNamespace.of("feature-flag");

    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureFlagUpdatePolicy featureFlagUpdatePolicy;
    private final LockExecutor lockExecutor;
    private final ServiceActionExecutor serviceActionExecutor;
    private final FeatureFlagCache featureFlagCache;

    @Transactional
    public FeatureFlag create(String key, FeatureValue defaultValue) {
        return executeUnderLock(key, () -> {
            if (featureFlagRepository.existsByKey(key)) {
                throw new FeatureFlagAlreadyExistsException("Feature flag '%s' already exists".formatted(key));
            }

            FeatureFlag featureFlag = buildFeatureFlag(key, defaultValue);
            featureFlagRepository.save(featureFlag);
            invalidateCacheAfterCommit(key);

            return featureFlag;
        });
    }

    private FeatureFlag buildFeatureFlag(String key, FeatureValue defaultValue) {
        return new FeatureFlag(UUID.randomUUID(), key, defaultValue, 0L);
    }

    @Transactional
    public FeatureFlag update(String key, FeatureValue defaultValue, long version) {
        return executeUnderLock(key, () -> {
            validateDefaultValueUpdateAllowed(key);
            updateAndCheckOptimisticLocking(key, defaultValue, version);
            invalidateCacheAfterCommit(key);

            return getByKey(key);
        });
    }

    private void updateAndCheckOptimisticLocking(String key, FeatureValue defaultValue, long version) {
        int affectedRows = featureFlagRepository.update(key, defaultValue, version);
        if (affectedRows == 0) {
            if (!featureFlagRepository.existsByKey(key)) {
                throw new FeatureFlagNotFoundException("Feature flag '%s' not found".formatted(key));
            }
            throw new OptimisticLockingFailureException(
                    "Feature flag '%s' version mismatch. Expected version %d".formatted(key, version));
        }
    }

    @Transactional(readOnly = true)
    public FeatureFlag getByKey(String key) {
        return featureFlagCache
                .getOrLoad(key, () -> featureFlagRepository.findByKey(key))
                .orElseThrow(() -> new FeatureFlagNotFoundException("Feature flag '%s' not found".formatted(key)));
    }

    private void validateDefaultValueUpdateAllowed(String key) {
        if (!featureFlagUpdatePolicy.canUpdateDefaultValue(key)) {
            throw new FeatureFlagUpdateBlockedException(
                    "Feature flag '%s' default value cannot be updated while an experiment exists".formatted(key));
        }
    }

    private void invalidateCacheAfterCommit(String key) {
        serviceActionExecutor.executeAfterCommit(() -> featureFlagCache.invalidate(key));
    }

    private <T> T executeUnderLock(String key, Supplier<T> action) {
        return lockExecutor.withLock(FEATURE_FLAG_LOCK_NAMESPACE, key, action);
    }
}
