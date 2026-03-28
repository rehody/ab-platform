package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.cache.ExperimentCache;
import io.github.rehody.abplatform.dto.request.ExperimentCreateRequest;
import io.github.rehody.abplatform.dto.request.ExperimentUpdateRequest;
import io.github.rehody.abplatform.dto.response.ExperimentResponse;
import io.github.rehody.abplatform.exception.ExperimentAlreadyExistsException;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.repository.ExperimentRepository.ReplaceVariantsResult;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentService {

    private static final LockNamespace EXPERIMENT_LOCK_NAMESPACE = LockNamespace.of("experiment");

    private final ExperimentRepository experimentRepository;
    private final LockExecutor lockExecutor;
    private final ServiceActionExecutor serviceActionExecutor;
    private final ExperimentCache experimentCache;

    @Transactional
    public ExperimentResponse create(ExperimentCreateRequest request) {
        String flagKey = request.flagKey();
        return executeUnderLock(flagKey, () -> {
            ensureExperimentNotExists(flagKey);

            Experiment experiment = buildExperiment(request);
            experimentRepository.save(experiment);
            invalidateCacheAfterCommit(flagKey);

            return ExperimentResponse.from(experiment);
        });
    }

    private Experiment buildExperiment(ExperimentCreateRequest request) {
        return new Experiment(UUID.randomUUID(), request.flagKey(), request.variants(), request.state(), 0L);
    }

    private void ensureExperimentNotExists(String flagKey) {
        if (experimentRepository.existsByFlagKey(flagKey)) {
            throw new ExperimentAlreadyExistsException(
                    "Experiment with flag key '%s' already exists".formatted(flagKey));
        }
    }

    @Transactional
    public ExperimentResponse update(UUID id, ExperimentUpdateRequest request) {
        String flagKey = findFlagKeyByIdOrThrow(id);

        return executeUnderLock(flagKey, () -> {
            replaceVariantsAndCheckOptimisticLocking(id, request);
            invalidateCacheAfterCommit(flagKey);
            Experiment experiment = findByIdOrThrow(id);
            return ExperimentResponse.from(experiment);
        });
    }

    @Transactional(readOnly = true)
    public ExperimentResponse getById(UUID id) {
        String flagKey = findFlagKeyByIdOrThrow(id);

        return experimentCache
                .getOrLoad(
                        flagKey,
                        () -> experimentRepository.findByFlagKey(flagKey).map(ExperimentResponse::from))
                .orElseThrow(() -> new ExperimentNotFoundException("Experiment '%s' not found".formatted(id)));
    }

    @Transactional(readOnly = true)
    public List<ExperimentResponse> getAll() {
        return experimentRepository.findAll().stream()
                .map(ExperimentResponse::from)
                .toList();
    }

    private void replaceVariantsAndCheckOptimisticLocking(UUID experimentId, ExperimentUpdateRequest request) {
        ReplaceVariantsResult result =
                experimentRepository.replaceVariants(experimentId, request.version(), request.variants());

        switch (result) {
            case NOT_FOUND ->
                throw new ExperimentNotFoundException("Experiment '%s' not found".formatted(experimentId));

            case VERSION_CONFLICT ->
                throw new OptimisticLockingFailureException("Experiment '%s' version mismatch. Expected version %d"
                        .formatted(experimentId, request.version()));
        }
    }

    private Experiment findByIdOrThrow(UUID id) {
        return experimentRepository
                .findById(id)
                .orElseThrow(() -> new ExperimentNotFoundException("Experiment '%s' not found".formatted(id)));
    }

    private String findFlagKeyByIdOrThrow(UUID id) {
        return experimentRepository
                .findFlagKeyById(id)
                .orElseThrow(() -> new ExperimentNotFoundException("Experiment '%s' not found".formatted(id)));
    }

    private void invalidateCacheAfterCommit(String flagKey) {
        serviceActionExecutor.executeAfterCommit(() -> experimentCache.invalidate(flagKey));
    }

    private <T> T executeUnderLock(String key, Supplier<T> action) {
        return lockExecutor.withLock(EXPERIMENT_LOCK_NAMESPACE, key, action);
    }
}
