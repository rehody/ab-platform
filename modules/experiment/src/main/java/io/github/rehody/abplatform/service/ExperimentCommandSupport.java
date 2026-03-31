package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.cache.ExperimentCache;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import io.github.rehody.abplatform.util.lock.LockExecutor;
import io.github.rehody.abplatform.util.lock.LockNamespace;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExperimentCommandSupport {

    private static final LockNamespace EXPERIMENT_LOCK_NAMESPACE = LockNamespace.of("experiment");

    private final ExperimentRepository experimentRepository;
    private final LockExecutor lockExecutor;
    private final ServiceActionExecutor serviceActionExecutor;
    private final ExperimentCache experimentCache;

    public String getFlagKeyById(UUID id) {
        return experimentRepository
                .findFlagKeyById(id)
                .orElseThrow(() -> new ExperimentNotFoundException("Experiment '%s' not found".formatted(id)));
    }

    public Experiment getById(UUID id) {
        return experimentRepository
                .findById(id)
                .orElseThrow(() -> new ExperimentNotFoundException("Experiment '%s' not found".formatted(id)));
    }

    public <T> T withExperimentLock(String flagKey, Supplier<T> action) {
        return lockExecutor.withLock(EXPERIMENT_LOCK_NAMESPACE, flagKey, action);
    }

    public void invalidateCacheAfterCommit(String flagKey) {
        serviceActionExecutor.executeAfterCommit(() -> experimentCache.invalidate(flagKey));
    }
}
