package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.cache.ExperimentCache;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentQueryService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentCommandSupport experimentCommandSupport;
    private final ExperimentCache experimentCache;

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
}
