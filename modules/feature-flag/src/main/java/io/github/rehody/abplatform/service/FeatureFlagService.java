package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.cache.FeatureFlagCache;
import io.github.rehody.abplatform.dto.request.FeatureFlagCreateRequest;
import io.github.rehody.abplatform.dto.request.FeatureFlagUpdateRequest;
import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.exception.FeatureFlagAlreadyExistsException;
import io.github.rehody.abplatform.exception.FeatureFlagNotFoundException;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.repository.FeatureFlagRepository;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private static final LockNamespace FEATURE_FLAG_LOCK_NAMESPACE = LockNamespace.of("feature-flag");

    private final FeatureFlagRepository featureFlagRepository;
    private final LockExecutor lockExecutor;
    private final FeatureFlagCache featureFlagCache;

    @Transactional
    public FeatureFlagResponse create(FeatureFlagCreateRequest request) {
        String key = request.key();
        return executeUnderLock(key, () -> {
            if (featureFlagRepository.existsByKey(key)) {
                throw new FeatureFlagAlreadyExistsException("Feature flag '%s' already exists".formatted(key));
            }

            FeatureFlag featureFlag = buildFeatureFlag(key, request.defaultValue());
            featureFlagRepository.save(featureFlag);
            invalidateCacheAfterCommit(key);

            return FeatureFlagResponse.from(featureFlag);
        });
    }

    private FeatureFlag buildFeatureFlag(String key, FeatureValue defaultValue) {
        return new FeatureFlag(UUID.randomUUID(), key, defaultValue, 0L);
    }

    @Transactional
    public FeatureFlagResponse update(String key, FeatureFlagUpdateRequest request) {
        return executeUnderLock(key, () -> {
            updateAndCheckOptimisticLocking(key, request);
            invalidateCacheAfterCommit(key);

            FeatureFlag featureFlag = findByKeyOrThrow(key);
            return FeatureFlagResponse.from(featureFlag);
        });
    }

    private void updateAndCheckOptimisticLocking(String key, FeatureFlagUpdateRequest request) {
        int affectedRows = featureFlagRepository.update(key, request.defaultValue(), request.version());
        if (affectedRows == 0) {
            if (!featureFlagRepository.existsByKey(key)) {
                throw new FeatureFlagNotFoundException("Feature flag '%s' not found".formatted(key));
            }
            throw new OptimisticLockingFailureException(
                    "Feature flag '%s' version mismatch. Expected version %d".formatted(key, request.version()));
        }
    }

    private FeatureFlag findByKeyOrThrow(String key) {
        return featureFlagRepository
                .findByKey(key)
                .orElseThrow(() -> new FeatureFlagNotFoundException("Feature flag '%s' not found".formatted(key)));
    }

    @Transactional(readOnly = true)
    public FeatureFlagResponse getByKey(String key) {
        return featureFlagCache
                .getOrLoad(key, () -> featureFlagRepository.findByKey(key).map(FeatureFlagResponse::from))
                .orElseThrow(() -> new FeatureFlagNotFoundException("Feature flag '%s' not found".formatted(key)));
    }

    private void invalidateCacheAfterCommit(String key) {
        executeAfterCommit(() -> featureFlagCache.invalidate(key));
    }

    private void executeAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private <T> T executeUnderLock(String key, Supplier<T> action) {
        return lockExecutor.withLock(FEATURE_FLAG_LOCK_NAMESPACE, key, action);
    }
}
