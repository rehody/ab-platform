package io.github.rehody.abplatform.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.repository.jdbc.ExperimentJdbcRepository;
import io.github.rehody.abplatform.repository.jdbc.ExperimentVariantJdbcRepository;
import io.github.rehody.abplatform.repository.mapper.ExperimentAggregateMapper;
import io.github.rehody.abplatform.repository.sync.ExperimentVariantSynchronizer;
import io.github.rehody.abplatform.repository.validation.ExperimentVariantPreparer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentRepositoryTest {

    @Mock
    private ExperimentJdbcRepository experimentJdbcRepository;

    @Mock
    private ExperimentVariantJdbcRepository experimentVariantJdbcRepository;

    @Mock
    private ExperimentAggregateMapper experimentAggregateMapper;

    @Mock
    private ExperimentVariantSynchronizer experimentVariantSynchronizer;

    @Mock
    private ExperimentVariantPreparer experimentVariantPreparer;

    private ExperimentRepository experimentRepository;

    @BeforeEach
    void setUp() {
        experimentRepository = new ExperimentRepository(
                experimentJdbcRepository,
                experimentVariantJdbcRepository,
                experimentAggregateMapper,
                experimentVariantSynchronizer,
                experimentVariantPreparer);
    }

    @Test
    void save_shouldPrepareVariantsInsertExperimentAndBatchInsertVariants() {
        Experiment experiment = experiment("flag-a", 0L);
        List<ExperimentVariant> preparedVariants = variants();
        when(experimentVariantPreparer.prepare(experiment.id(), experiment.variants()))
                .thenReturn(preparedVariants);

        experimentRepository.save(experiment);

        verify(experimentVariantPreparer).prepare(experiment.id(), experiment.variants());
        verify(experimentJdbcRepository).insert(experiment);
        verify(experimentVariantJdbcRepository).batchInsert(experiment.id(), preparedVariants);
    }

    @Test
    void findById_shouldReturnMappedAggregateWhenExperimentExists() {
        Experiment experiment = experiment("flag-b", 2L);
        Experiment mapped = experiment("flag-b", 2L);
        List<ExperimentVariant> variants = variants();
        when(experimentJdbcRepository.findById(experiment.id())).thenReturn(Optional.of(experiment));
        when(experimentVariantJdbcRepository.findByExperimentId(experiment.id()))
                .thenReturn(variants);
        when(experimentAggregateMapper.withVariants(experiment, variants)).thenReturn(mapped);

        Optional<Experiment> result = experimentRepository.findById(experiment.id());

        assertThat(result).contains(mapped);
    }

    @Test
    void findById_shouldReturnEmptyWhenExperimentMissing() {
        UUID id = UUID.randomUUID();
        when(experimentJdbcRepository.findById(id)).thenReturn(Optional.empty());

        Optional<Experiment> result = experimentRepository.findById(id);

        assertThat(result).isEmpty();
        verify(experimentVariantJdbcRepository, never()).findByExperimentId(any());
    }

    @Test
    void findByFlagKey_shouldReturnMappedAggregateWhenExperimentExists() {
        Experiment experiment = experiment("flag-c", 3L);
        Experiment mapped = experiment("flag-c", 3L);
        List<ExperimentVariant> variants = variants();
        when(experimentJdbcRepository.findByFlagKey("flag-c")).thenReturn(Optional.of(experiment));
        when(experimentVariantJdbcRepository.findByExperimentId(experiment.id()))
                .thenReturn(variants);
        when(experimentAggregateMapper.withVariants(experiment, variants)).thenReturn(mapped);

        Optional<Experiment> result = experimentRepository.findByFlagKey("flag-c");

        assertThat(result).contains(mapped);
    }

    @Test
    void findAll_shouldReturnEmptyListWhenNoExperimentsExist() {
        when(experimentJdbcRepository.findAll()).thenReturn(List.of());

        List<Experiment> result = experimentRepository.findAll();

        assertThat(result).isEmpty();
        verify(experimentVariantJdbcRepository, never()).findByExperimentIds(any());
    }

    @Test
    void findAll_shouldMapVariantsForEachExperimentAndDefaultToEmptyVariants() {
        Experiment first = experiment("flag-d", 1L);
        Experiment second = experiment("flag-e", 2L);
        Experiment mappedFirst = experiment("flag-d", 1L);
        Experiment mappedSecond = experiment("flag-e", 2L);
        List<ExperimentVariant> firstVariants = variants();
        when(experimentJdbcRepository.findAll()).thenReturn(List.of(first, second));
        when(experimentVariantJdbcRepository.findByExperimentIds(List.of(first.id(), second.id())))
                .thenReturn(Map.of(first.id(), firstVariants));
        when(experimentAggregateMapper.withVariants(first, firstVariants)).thenReturn(mappedFirst);
        when(experimentAggregateMapper.withVariants(second, List.of())).thenReturn(mappedSecond);

        List<Experiment> result = experimentRepository.findAll();

        assertThat(result).containsExactly(mappedFirst, mappedSecond);
    }

    @Test
    void existsById_shouldDelegateToJdbcRepository() {
        UUID id = UUID.randomUUID();
        when(experimentJdbcRepository.existsById(id)).thenReturn(true);

        assertThat(experimentRepository.existsById(id)).isTrue();
    }

    @Test
    void update_shouldReturnUpdatedOutcomeWithNewVersionWhenJdbcUpdateSucceeds() {
        Experiment experiment = experiment("flag-f", 5L);
        when(experimentJdbcRepository.update(experiment)).thenReturn(Optional.of(6L));

        ExperimentRepository.UpdateOutcome result = experimentRepository.update(experiment);

        assertThat(result.status()).isEqualTo(ExperimentRepository.UpdateStatus.UPDATED);
        assertThat(result.version()).isEqualTo(6L);
    }

    @Test
    void update_shouldReturnVersionConflictWhenExperimentExistsButVersionDiffers() {
        Experiment experiment = experiment("flag-g", 6L);
        when(experimentJdbcRepository.update(experiment)).thenReturn(Optional.empty());
        when(experimentJdbcRepository.findVersionById(experiment.id())).thenReturn(Optional.of(7L));

        ExperimentRepository.UpdateOutcome result = experimentRepository.update(experiment);

        assertThat(result.status()).isEqualTo(ExperimentRepository.UpdateStatus.VERSION_CONFLICT);
        assertThat(result.version()).isNull();
    }

    @Test
    void update_shouldReturnNotFoundWhenExperimentMissing() {
        Experiment experiment = experiment("flag-h", 6L);
        when(experimentJdbcRepository.update(experiment)).thenReturn(Optional.empty());
        when(experimentJdbcRepository.findVersionById(experiment.id())).thenReturn(Optional.empty());

        ExperimentRepository.UpdateOutcome result = experimentRepository.update(experiment);

        assertThat(result.status()).isEqualTo(ExperimentRepository.UpdateStatus.NOT_FOUND);
        assertThat(result.version()).isNull();
    }

    @Test
    void deleteById_shouldDelegateToJdbcRepository() {
        UUID id = UUID.randomUUID();
        when(experimentJdbcRepository.deleteById(id)).thenReturn(1);

        int deletedRows = experimentRepository.deleteById(id);

        assertThat(deletedRows).isEqualTo(1);
    }

    @Test
    void findVariantsByExperimentId_shouldDelegateToVariantJdbcRepository() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        when(experimentVariantJdbcRepository.findByExperimentId(experimentId)).thenReturn(variants);

        assertThat(experimentRepository.findVariantsByExperimentId(experimentId))
                .isEqualTo(variants);
    }

    @Test
    void findVariantsByExperimentIds_shouldDelegateToVariantJdbcRepository() {
        UUID experimentId = UUID.randomUUID();
        Map<UUID, List<ExperimentVariant>> variantsByExperimentId = Map.of(experimentId, variants());
        when(experimentVariantJdbcRepository.findByExperimentIds(List.of(experimentId)))
                .thenReturn(variantsByExperimentId);

        assertThat(experimentRepository.findVariantsByExperimentIds(List.of(experimentId)))
                .isEqualTo(variantsByExperimentId);
    }

    @Test
    void replaceVariants_shouldPrepareIncrementVersionSyncAndReturnUpdated() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        List<ExperimentVariant> preparedVariants = variants();
        when(experimentVariantPreparer.prepare(experimentId, variants)).thenReturn(preparedVariants);
        when(experimentJdbcRepository.incrementVersion(experimentId, 3L)).thenReturn(1);

        ExperimentRepository.ReplaceVariantsResult result =
                experimentRepository.replaceVariants(experimentId, 3L, variants);

        assertThat(result).isEqualTo(ExperimentRepository.ReplaceVariantsResult.UPDATED);
        verify(experimentVariantSynchronizer).sync(experimentId, preparedVariants);
    }

    @Test
    void replaceVariants_shouldReturnVersionConflictWhenExperimentExistsButVersionDiffers() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        when(experimentVariantPreparer.prepare(experimentId, variants)).thenReturn(variants);
        when(experimentJdbcRepository.incrementVersion(experimentId, 4L)).thenReturn(0);
        when(experimentJdbcRepository.findVersionById(experimentId)).thenReturn(Optional.of(5L));

        ExperimentRepository.ReplaceVariantsResult result =
                experimentRepository.replaceVariants(experimentId, 4L, variants);

        assertThat(result).isEqualTo(ExperimentRepository.ReplaceVariantsResult.VERSION_CONFLICT);
        verify(experimentVariantSynchronizer, never()).sync(any(), any());
    }

    @Test
    void replaceVariants_shouldReturnNotFoundWhenExperimentMissing() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants = variants();
        when(experimentVariantPreparer.prepare(experimentId, variants)).thenReturn(variants);
        when(experimentJdbcRepository.incrementVersion(experimentId, 4L)).thenReturn(0);
        when(experimentJdbcRepository.findVersionById(experimentId)).thenReturn(Optional.empty());

        ExperimentRepository.ReplaceVariantsResult result =
                experimentRepository.replaceVariants(experimentId, 4L, variants);

        assertThat(result).isEqualTo(ExperimentRepository.ReplaceVariantsResult.NOT_FOUND);
        verify(experimentVariantSynchronizer, never()).sync(any(), any());
    }

    @Test
    void existsByFlagKey_shouldDelegateToJdbcRepository() {
        when(experimentJdbcRepository.existsByFlagKey("flag-i")).thenReturn(true);

        assertThat(experimentRepository.existsByFlagKey("flag-i")).isTrue();
    }

    @Test
    void findFlagKeyById_shouldDelegateToJdbcRepository() {
        UUID id = UUID.randomUUID();
        when(experimentJdbcRepository.findFlagKeyById(id)).thenReturn(Optional.of("flag-j"));

        assertThat(experimentRepository.findFlagKeyById(id)).contains("flag-j");
    }

    private Experiment experiment(String flagKey, long version) {
        return new Experiment(UUID.randomUUID(), flagKey, variants(), ExperimentState.RUNNING, version, null, null);
    }

    private List<ExperimentVariant> variants() {
        return List.of(new ExperimentVariant(
                UUID.randomUUID(), "control", new FeatureValue(true, FeatureValueType.BOOL), 0, BigDecimal.ONE));
    }
}
