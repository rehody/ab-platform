package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.cache.FeatureFlagCache;
import io.github.rehody.abplatform.exception.FeatureFlagAlreadyExistsException;
import io.github.rehody.abplatform.exception.FeatureFlagNotFoundException;
import io.github.rehody.abplatform.exception.FeatureFlagUpdateBlockedException;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.audit.AuditAction;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.model.audit.AuditDetails;
import io.github.rehody.abplatform.model.audit.AuditTarget;
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
    private final ActionExecutorService actionExecutorService;
    private final FeatureFlagCache featureFlagCache;
    private final AuditService auditService;

    @Transactional
    public FeatureFlag create(AuditActor actor, String key, FeatureValue defaultValue) {
        return executeUnderLock(key, () -> {
            if (featureFlagRepository.existsByKey(key)) {
                throw new FeatureFlagAlreadyExistsException("Feature flag '%s' already exists".formatted(key));
            }

            FeatureFlag featureFlag = buildFeatureFlag(key, defaultValue);
            featureFlagRepository.save(featureFlag);
            writeCreateAudit(actor, featureFlag);
            invalidateCacheAfterCommit(key);

            return featureFlag;
        });
    }

    private FeatureFlag buildFeatureFlag(String key, FeatureValue defaultValue) {
        return new FeatureFlag(UUID.randomUUID(), key, defaultValue, 0L);
    }

    @Transactional
    public FeatureFlag update(AuditActor actor, String key, FeatureValue defaultValue, long version) {
        return executeUnderLock(key, () -> {
            FeatureFlag currentFeatureFlag = getRequiredFeatureFlag(key);
            validateDefaultValueUpdateAllowed(key);
            updateFeatureFlag(key, defaultValue, version);
            invalidateCacheAfterCommit(key);
            FeatureFlag updatedFeatureFlag = getRequiredFeatureFlag(key);
            writeUpdateAudit(actor, currentFeatureFlag, updatedFeatureFlag);
            return updatedFeatureFlag;
        });
    }

    private void updateFeatureFlag(String key, FeatureValue defaultValue, long version) {
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

    private FeatureFlag getRequiredFeatureFlag(String key) {
        return featureFlagRepository
                .findByKey(key)
                .orElseThrow(() -> new FeatureFlagNotFoundException("Feature flag '%s' not found".formatted(key)));
    }

    private void invalidateCacheAfterCommit(String key) {
        actionExecutorService.executeAfterCommit(() -> featureFlagCache.invalidate(key));
    }

    private <T> T executeUnderLock(String key, Supplier<T> action) {
        return lockExecutor.withLock(FEATURE_FLAG_LOCK_NAMESPACE, key, action);
    }

    private void writeCreateAudit(AuditActor actor, FeatureFlag featureFlag) {
        auditService.write(
                actor,
                AuditAction.FEATURE_FLAG_CREATED,
                AuditTarget.featureFlag(featureFlag.id()),
                AuditDetails.entry("key", featureFlag.key())
                        .with("defaultValue", featureFlag.defaultValue().toString()));
    }

    private void writeUpdateAudit(AuditActor actor, FeatureFlag currentFeatureFlag, FeatureFlag updatedFeatureFlag) {
        auditService.write(
                actor,
                AuditAction.FEATURE_FLAG_UPDATED,
                AuditTarget.featureFlag(updatedFeatureFlag.id()),
                AuditDetails.transition(
                        "defaultValue", currentFeatureFlag.defaultValue(), updatedFeatureFlag.defaultValue()));
    }
}
