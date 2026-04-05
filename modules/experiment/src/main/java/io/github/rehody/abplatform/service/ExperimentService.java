package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.cache.ExperimentCache;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.exception.ExperimentAlreadyExistsException;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
import io.github.rehody.abplatform.policy.ExperimentTimestampPolicy;
import io.github.rehody.abplatform.policy.ExperimentVariantPolicy;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.UpdateOutcome;
import io.github.rehody.abplatform.repository.jdbc.ExperimentDomainJdbcRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentCommandSupport experimentCommandSupport;
    private final ExperimentCache experimentCache;
    private final ExperimentAssignmentPolicy experimentAssignmentPolicy;
    private final ExperimentTimestampPolicy experimentTimestampPolicy;
    private final FeatureFlagService featureFlagService;
    private final ExperimentVariantPolicy experimentVariantPolicy;
    private final ExperimentDomainJdbcRepository experimentDomainJdbcRepository;

    @Transactional
    public Experiment create(
            String flagKey, String domainKey, List<ExperimentVariant> variants, ExperimentState state) {
        return experimentCommandSupport.withExperimentLock(flagKey, () -> {
            ensureExperimentNotExists(flagKey);
            ensureDomainExists(domainKey);

            FeatureFlag featureFlag = featureFlagService.getByKey(flagKey);

            UUID experimentId = UUID.randomUUID();
            validateVariantsForFlagDefault(experimentId, variants, featureFlag);

            Experiment experiment = buildExperiment(experimentId, flagKey, domainKey, variants, state);

            experimentAssignmentPolicy.validateAssignmentInvariants(experiment);
            experimentRepository.save(experiment);
            experimentCommandSupport.invalidateCacheAfterCommit(flagKey);

            return experiment;
        });
    }

    private Experiment buildExperiment(
            UUID experimentId,
            String flagKey,
            String domainKey,
            List<ExperimentVariant> variants,
            ExperimentState state) {
        Experiment experiment = new Experiment(experimentId, flagKey, domainKey, variants, state, 0L, null, null);
        return experimentTimestampPolicy.initializeTimestamps(experiment, Instant.now());
    }

    private void ensureExperimentNotExists(String flagKey) {
        if (experimentRepository.existsByFlagKey(flagKey)) {
            throw new ExperimentAlreadyExistsException(
                    "Experiment with flag key '%s' already exists".formatted(flagKey));
        }
    }

    @Transactional
    public Experiment update(
            UUID id, String flagKey, String domainKey, List<ExperimentVariant> variants, long version) {
        Experiment currentExperiment = experimentCommandSupport.getById(id);
        String resolvedFlagKey = resolveFlagKey(currentExperiment, flagKey);
        String resolvedDomainKey = resolveDomainKey(currentExperiment, domainKey);

        return experimentCommandSupport.withExperimentLocks(
                List.of(currentExperiment.flagKey(), resolvedFlagKey), () -> {
                    Experiment updatedExperiment = buildUpdatedExperiment(
                                    currentExperiment, resolvedFlagKey, resolvedDomainKey, variants)
                            .withVersion(version);

                    validateUpdatedExperiment(currentExperiment.id(), updatedExperiment);

                    long updatedVersion = updateWithVariants(updatedExperiment, version);
                    invalidateRelevantCaches(currentExperiment.flagKey(), resolvedFlagKey);

                    return updatedExperiment.withVersion(updatedVersion);
                });
    }

    @Transactional(readOnly = true)
    public Experiment getById(UUID id) {
        String flagKey = experimentCommandSupport.getFlagKeyById(id);

        return experimentCache
                .getOrLoad(flagKey, () -> experimentRepository.findByFlagKey(flagKey))
                .orElseThrow(() -> new ExperimentNotFoundException("Experiment '%s' not found".formatted(id)));
    }

    @Transactional(readOnly = true)
    public List<Experiment> getAll() {
        return experimentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Experiment> getRunning() {
        return experimentRepository.findRunning();
    }

    @Transactional(readOnly = true)
    public Optional<Experiment> findByFlagKey(String flagKey) {
        return experimentCache.getOrLoad(flagKey, () -> experimentRepository.findByFlagKey(flagKey));
    }

    @Transactional(readOnly = true)
    public void ensureExistsById(UUID id) {
        if (!experimentRepository.existsById(id)) {
            throw new ExperimentNotFoundException("Experiment '%s' not found".formatted(id));
        }
    }

    private long updateWithVariants(Experiment experiment, long version) {
        UpdateOutcome outcome = experimentRepository.updateWithVariants(experiment);
        return switch (outcome.status()) {
            case NOT_FOUND ->
                throw new ExperimentNotFoundException("Experiment '%s' not found".formatted(experiment.id()));
            case VERSION_CONFLICT ->
                throw new OptimisticLockingFailureException(
                        "Experiment '%s' version mismatch. Expected version %d".formatted(experiment.id(), version));
            case UPDATED -> outcome.version();
        };
    }

    private void validateUpdatedExperiment(UUID experimentId, Experiment updatedExperiment) {
        ensureDomainExists(updatedExperiment.domainKey());
        ensureFlagKeyAvailable(experimentId, updatedExperiment.flagKey());

        FeatureFlag featureFlag = featureFlagService.getByKey(updatedExperiment.flagKey());
        validateVariantsForFlagDefault(experimentId, updatedExperiment.variants(), featureFlag);
        experimentAssignmentPolicy.validateAssignmentInvariants(updatedExperiment);
    }

    private Experiment buildUpdatedExperiment(
            Experiment currentExperiment, String flagKey, String domainKey, List<ExperimentVariant> variants) {
        return new Experiment(
                currentExperiment.id(),
                flagKey,
                domainKey,
                variants,
                currentExperiment.state(),
                currentExperiment.version(),
                currentExperiment.startedAt(),
                currentExperiment.completedAt());
    }

    private String resolveFlagKey(Experiment experiment, String flagKey) {
        if (flagKey != null) {
            return flagKey;
        }
        return experiment.flagKey();
    }

    private String resolveDomainKey(Experiment experiment, String domainKey) {
        if (domainKey != null) {
            return domainKey;
        }
        return experiment.domainKey();
    }

    private void ensureFlagKeyAvailable(UUID experimentId, String flagKey) {
        experimentRepository
                .findByFlagKey(flagKey)
                .filter(existingExperiment -> !existingExperiment.id().equals(experimentId))
                .ifPresent(_ -> {
                    throw new ExperimentAlreadyExistsException(
                            "Experiment with flag key '%s' already exists".formatted(flagKey));
                });
    }

    private void ensureDomainExists(String domainKey) {
        if (!experimentDomainJdbcRepository.existsByKey(domainKey)) {
            throw new IllegalArgumentException("Unknown experiment domainKey '%s'".formatted(domainKey));
        }
    }

    private void invalidateRelevantCaches(String currentFlagKey, String updatedFlagKey) {
        experimentCommandSupport.invalidateCacheAfterCommit(currentFlagKey);
        if (!currentFlagKey.equals(updatedFlagKey)) {
            experimentCommandSupport.invalidateCacheAfterCommit(updatedFlagKey);
        }
    }

    private void validateVariantsForFlagDefault(
            UUID experimentId, List<ExperimentVariant> variants, FeatureFlag featureFlag) {
        experimentVariantPolicy.validateVariantConfiguration(experimentId, variants, featureFlag.defaultValue());
    }
}
