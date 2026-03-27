package io.github.rehody.abplatform.repository;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.repository.jdbc.ExperimentJdbcRepository;
import io.github.rehody.abplatform.repository.jdbc.ExperimentVariantJdbcRepository;
import io.github.rehody.abplatform.repository.mapper.ExperimentAggregateMapper;
import io.github.rehody.abplatform.repository.sync.ExperimentVariantSynchronizer;
import io.github.rehody.abplatform.repository.validation.ExperimentVariantPreparer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ExperimentRepository {

    private final ExperimentJdbcRepository experimentJdbcRepository;
    private final ExperimentVariantJdbcRepository experimentVariantJdbcRepository;
    private final ExperimentAggregateMapper experimentAggregateMapper;
    private final ExperimentVariantSynchronizer experimentVariantSynchronizer;
    private final ExperimentVariantPreparer experimentVariantPreparer;

    @Transactional
    public void save(Experiment experiment, List<ExperimentVariant> variants) {
        List<ExperimentVariant> preparedVariants = experimentVariantPreparer.prepare(experiment.id(), variants);
        experimentJdbcRepository.insert(experiment);
        experimentVariantJdbcRepository.batchInsert(experiment.id(), preparedVariants);
    }

    public Optional<Experiment> findById(UUID id) {
        return experimentJdbcRepository
                .findById(id)
                .map(experiment -> experimentAggregateMapper.withVariants(
                        experiment, findVariantsByExperimentId(experiment.id())));
    }

    public List<Experiment> findAll() {
        List<Experiment> experiments = experimentJdbcRepository.findAll();
        if (experiments.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<ExperimentVariant>> variantsByExperimentId = findVariantsByExperimentIds(
                experiments.stream().map(Experiment::id).toList());

        return experiments.stream()
                .map(experiment -> experimentAggregateMapper.withVariants(
                        experiment, variantsByExperimentId.getOrDefault(experiment.id(), List.of())))
                .toList();
    }

    public boolean existsById(UUID id) {
        return experimentJdbcRepository.existsById(id);
    }

    @Transactional
    public UpdateResult update(Experiment experiment) {
        int affectedRows = experimentJdbcRepository.update(experiment);
        if (affectedRows == 1) {
            return UpdateResult.UPDATED;
        }

        return experimentJdbcRepository
                .findVersionById(experiment.id())
                .map(_ -> UpdateResult.VERSION_CONFLICT)
                .orElse(UpdateResult.NOT_FOUND);
    }

    @Transactional
    public int deleteById(UUID id) {
        return experimentJdbcRepository.deleteById(id);
    }

    public List<ExperimentVariant> findVariantsByExperimentId(UUID experimentId) {
        return experimentVariantJdbcRepository.findByExperimentId(experimentId);
    }

    public Map<UUID, List<ExperimentVariant>> findVariantsByExperimentIds(List<UUID> experimentIds) {
        return experimentVariantJdbcRepository.findByExperimentIds(experimentIds);
    }

    @Transactional
    public ReplaceVariantsResult replaceVariants(
            UUID experimentId, long expectedVersion, List<ExperimentVariant> variants) {
        List<ExperimentVariant> preparedVariants = experimentVariantPreparer.prepare(experimentId, variants);
        int affectedRows = experimentJdbcRepository.incrementVersion(experimentId, expectedVersion);
        if (affectedRows == 0) {
            return experimentJdbcRepository
                    .findVersionById(experimentId)
                    .map(_ -> ReplaceVariantsResult.VERSION_CONFLICT)
                    .orElse(ReplaceVariantsResult.NOT_FOUND);
        }

        experimentVariantSynchronizer.sync(experimentId, preparedVariants);
        return ReplaceVariantsResult.UPDATED;
    }

    public enum UpdateResult {
        UPDATED,
        NOT_FOUND,
        VERSION_CONFLICT
    }

    public enum ReplaceVariantsResult {
        UPDATED,
        NOT_FOUND,
        VERSION_CONFLICT
    }
}
