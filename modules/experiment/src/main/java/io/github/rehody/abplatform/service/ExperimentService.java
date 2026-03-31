package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.cache.CachedExperiment;
import io.github.rehody.abplatform.cache.ExperimentCache;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.exception.ExperimentAlreadyExistsException;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.policy.ExperimentAssignmentPolicy;
import io.github.rehody.abplatform.policy.ExperimentTimestampPolicy;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.ReplaceVariantsResult;
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

    @Transactional
    public Experiment create(String flagKey, List<ExperimentVariant> variants, ExperimentState state) {
        return experimentCommandSupport.withExperimentLock(flagKey, () -> {
            ensureExperimentNotExists(flagKey);

            Experiment experiment = buildExperiment(flagKey, variants, state);
            experimentAssignmentPolicy.validateAssignmentInvariants(experiment);
            experimentRepository.save(experiment);
            experimentCommandSupport.invalidateCacheAfterCommit(flagKey);

            return experiment;
        });
    }

    private Experiment buildExperiment(String flagKey, List<ExperimentVariant> variants, ExperimentState state) {
        Experiment experiment = new Experiment(UUID.randomUUID(), flagKey, variants, state, 0L, null, null);
        return experimentTimestampPolicy.initializeTimestamps(experiment, Instant.now());
    }

    private void ensureExperimentNotExists(String flagKey) {
        if (experimentRepository.existsByFlagKey(flagKey)) {
            throw new ExperimentAlreadyExistsException(
                    "Experiment with flag key '%s' already exists".formatted(flagKey));
        }
    }

    @Transactional
    public Experiment update(UUID id, List<ExperimentVariant> variants, long version) {
        String flagKey = experimentCommandSupport.getFlagKeyById(id);

        return experimentCommandSupport.withExperimentLock(flagKey, () -> {
            validateAssignmentInvariantsForUpdatedVariants(id, variants);
            replaceVariantsAndCheckOptimisticLocking(id, variants, version);
            experimentCommandSupport.invalidateCacheAfterCommit(flagKey);
            return experimentCommandSupport.getById(id);
        });
    }

    @Transactional(readOnly = true)
    public Experiment getById(UUID id) {
        String flagKey = experimentCommandSupport.getFlagKeyById(id);

        return experimentCache
                .getOrLoad(
                        flagKey,
                        () -> experimentRepository.findByFlagKey(flagKey).map(CachedExperiment::from))
                .map(CachedExperiment::toModel)
                .orElseThrow(() -> new ExperimentNotFoundException("Experiment '%s' not found".formatted(id)));
    }

    @Transactional(readOnly = true)
    public List<Experiment> getAll() {
        return experimentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Experiment> findByFlagKey(String flagKey) {
        return experimentCache
                .getOrLoad(
                        flagKey,
                        () -> experimentRepository.findByFlagKey(flagKey).map(CachedExperiment::from))
                .map(CachedExperiment::toModel);
    }

    private void replaceVariantsAndCheckOptimisticLocking(
            UUID experimentId, List<ExperimentVariant> variants, long version) {
        ReplaceVariantsResult result = experimentRepository.replaceVariants(experimentId, version, variants);

        switch (result) {
            case NOT_FOUND ->
                throw new ExperimentNotFoundException("Experiment '%s' not found".formatted(experimentId));

            case VERSION_CONFLICT ->
                throw new OptimisticLockingFailureException(
                        "Experiment '%s' version mismatch. Expected version %d".formatted(experimentId, version));
        }
    }

    private void validateAssignmentInvariantsForUpdatedVariants(UUID experimentId, List<ExperimentVariant> variants) {
        Experiment currentExperiment = experimentCommandSupport.getById(experimentId);
        Experiment updatedExperiment = currentExperiment.withVariants(variants);
        experimentAssignmentPolicy.validateAssignmentInvariants(updatedExperiment);
    }
}
