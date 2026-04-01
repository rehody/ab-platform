package io.github.rehody.abplatform.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.config.AbstractIntegrationDatabaseTest;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.repository.jdbc.ExperimentJdbcRepository;
import io.github.rehody.abplatform.repository.jdbc.ExperimentVariantJdbcRepository;
import io.github.rehody.abplatform.repository.mapper.ExperimentAggregateMapper;
import io.github.rehody.abplatform.repository.rowmapper.ExperimentRowMapper;
import io.github.rehody.abplatform.repository.rowmapper.ExperimentVariantRowMapper;
import io.github.rehody.abplatform.repository.sync.ExperimentVariantSynchronizer;
import io.github.rehody.abplatform.repository.validation.ExperimentVariantPreparer;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import({
    ExperimentRepository.class,
    ExperimentJdbcRepository.class,
    ExperimentVariantJdbcRepository.class,
    ExperimentAggregateMapper.class,
    ExperimentVariantSynchronizer.class,
    ExperimentVariantPreparer.class,
    ExperimentRowMapper.class,
    ExperimentVariantRowMapper.class
})
class ExperimentRepositoryIntegrationTest extends AbstractIntegrationDatabaseTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ExperimentRepository experimentRepository;

    @SuppressWarnings("SqlWithoutWhere")
    @BeforeEach
    void setUp() {
        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS feature_flags (
                            id UUID PRIMARY KEY,
                            feature_key VARCHAR(255) UNIQUE NOT NULL,
                            default_value TEXT NOT NULL,
                            default_value_type VARCHAR(16) NOT NULL,
                            version BIGINT NOT NULL DEFAULT 0
                        )
                        """).update();

        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS experiments (
                            id UUID PRIMARY KEY,
                            flag_key VARCHAR(255) NOT NULL REFERENCES feature_flags (feature_key),
                            state VARCHAR(16) NOT NULL,
                            version BIGINT NOT NULL DEFAULT 0,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
                        )
                        """).update();

        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS experiment_variants (
                            id UUID PRIMARY KEY,
                            experiment_id UUID NOT NULL REFERENCES experiments (id) ON DELETE CASCADE,
                            key VARCHAR(255) NOT NULL,
                            value TEXT NOT NULL,
                            value_type VARCHAR(16) NOT NULL,
                            position INT NOT NULL,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                            updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                            UNIQUE (experiment_id, key),
                            UNIQUE (experiment_id, position)
                        )
                        """).update();

        jdbcClient.sql("DELETE FROM experiment_variants").update();
        jdbcClient.sql("DELETE FROM experiments").update();
        jdbcClient.sql("DELETE FROM feature_flags").update();
    }

    @Test
    void saveAndFindById_shouldPersistExperimentAndNormalizeVariants() {
        String flagKey = "checkout-redesign";
        insertFeatureFlag(flagKey);

        UUID experimentId = UUID.randomUUID();
        Experiment experiment = new Experiment(
                experimentId,
                flagKey,
                List.of(
                        new ExperimentVariant(
                                null, " control ", new FeatureValue(true, FeatureValueType.BOOL), 10, BigDecimal.ONE),
                        new ExperimentVariant(
                                UUID.randomUUID(),
                                "variant-a",
                                new FeatureValue("blue", FeatureValueType.STRING),
                                4,
                                BigDecimal.ONE)),
                ExperimentState.DRAFT,
                0L);

        experimentRepository.save(experiment);
        Experiment loaded = experimentRepository.findById(experimentId).orElseThrow();

        assertThat(loaded.id()).isEqualTo(experimentId);
        assertThat(loaded.flagKey()).isEqualTo(flagKey);
        assertThat(loaded.state()).isEqualTo(ExperimentState.DRAFT);
        assertThat(loaded.version()).isZero();
        assertThat(loaded.variants()).hasSize(2);
        assertThat(loaded.variants().getFirst().id()).isNotNull();
        assertThat(loaded.variants().getFirst().key()).isEqualTo("control");
        assertThat(loaded.variants().getFirst().position()).isZero();
        assertThat(loaded.variants().get(1).key()).isEqualTo("variant-a");
        assertThat(loaded.variants().get(1).position()).isEqualTo(1);
    }

    @Test
    void findByFlagKeyAndFindAll_shouldReturnPersistedExperimentsWithVariants() {
        String firstFlagKey = "flag-a";
        String secondFlagKey = "flag-b";
        insertFeatureFlag(firstFlagKey);
        insertFeatureFlag(secondFlagKey);

        Experiment first = new Experiment(
                UUID.randomUUID(),
                firstFlagKey,
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE)),
                ExperimentState.RUNNING,
                0L);
        Experiment second = new Experiment(
                UUID.randomUUID(),
                secondFlagKey,
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "variant-b",
                        new FeatureValue(10, FeatureValueType.NUMBER),
                        0,
                        BigDecimal.ONE)),
                ExperimentState.APPROVED,
                0L);

        experimentRepository.save(first);
        experimentRepository.save(second);

        Experiment byFlagKey = experimentRepository.findByFlagKey(secondFlagKey).orElseThrow();
        List<Experiment> all = experimentRepository.findAll().stream()
                .sorted(Comparator.comparing(Experiment::flagKey))
                .toList();

        assertThat(byFlagKey.id()).isEqualTo(second.id());
        assertThat(byFlagKey.variants()).hasSize(1);
        assertThat(byFlagKey.variants().getFirst().key()).isEqualTo("variant-b");

        assertThat(all).hasSize(2);
        assertThat(all.stream().map(Experiment::flagKey)).containsExactly("flag-a", "flag-b");
        assertThat(all.get(0).variants()).hasSize(1);
        assertThat(all.get(1).variants()).hasSize(1);
    }

    @Test
    void update_shouldReturnUpdatedAndIncrementVersionWhenExpectedVersionMatches() {
        String flagKey = "flag-c";
        insertFeatureFlag(flagKey);

        Experiment initial = new Experiment(
                UUID.randomUUID(),
                flagKey,
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE)),
                ExperimentState.DRAFT,
                0L);
        experimentRepository.save(initial);

        ExperimentRepository.UpdateOutcome result = experimentRepository.update(
                new Experiment(initial.id(), flagKey, initial.variants(), ExperimentState.RUNNING, 0L));
        Experiment updated = experimentRepository.findById(initial.id()).orElseThrow();

        assertThat(result.status()).isEqualTo(ExperimentRepository.UpdateStatus.UPDATED);
        assertThat(result.version()).isEqualTo(1L);
        assertThat(updated.state()).isEqualTo(ExperimentState.RUNNING);
        assertThat(updated.version()).isEqualTo(1L);
    }

    @Test
    void update_shouldReturnVersionConflictAndNotFoundForStaleAndMissingExperiments() {
        String flagKey = "flag-d";
        insertFeatureFlag(flagKey);

        Experiment persisted = new Experiment(
                UUID.randomUUID(),
                flagKey,
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE)),
                ExperimentState.DRAFT,
                0L);
        experimentRepository.save(persisted);

        ExperimentRepository.UpdateOutcome staleResult = experimentRepository.update(
                new Experiment(persisted.id(), flagKey, persisted.variants(), ExperimentState.APPROVED, 9L));
        ExperimentRepository.UpdateOutcome missingResult = experimentRepository.update(
                new Experiment(UUID.randomUUID(), flagKey, List.of(), ExperimentState.APPROVED, 0L));

        assertThat(staleResult.status()).isEqualTo(ExperimentRepository.UpdateStatus.VERSION_CONFLICT);
        assertThat(staleResult.version()).isNull();
        assertThat(missingResult.status()).isEqualTo(ExperimentRepository.UpdateStatus.NOT_FOUND);
        assertThat(missingResult.version()).isNull();
    }

    @Test
    void replaceVariants_shouldSynchronizeChildrenAndIncrementVersion() {
        String flagKey = "flag-e";
        insertFeatureFlag(flagKey);

        UUID keptVariantId = UUID.randomUUID();
        UUID deletedVariantId = UUID.randomUUID();
        Experiment experiment = new Experiment(
                UUID.randomUUID(),
                flagKey,
                List.of(
                        new ExperimentVariant(
                                keptVariantId,
                                "control",
                                new FeatureValue(true, FeatureValueType.BOOL),
                                0,
                                BigDecimal.ONE),
                        new ExperimentVariant(
                                deletedVariantId,
                                "old",
                                new FeatureValue("old", FeatureValueType.STRING),
                                1,
                                BigDecimal.ONE)),
                ExperimentState.RUNNING,
                0L);
        experimentRepository.save(experiment);

        ExperimentRepository.ReplaceVariantsResult result = experimentRepository.replaceVariants(
                experiment.id(),
                0L,
                List.of(
                        new ExperimentVariant(
                                keptVariantId,
                                "control",
                                new FeatureValue(false, FeatureValueType.BOOL),
                                8,
                                BigDecimal.ONE),
                        new ExperimentVariant(
                                null,
                                " new-variant ",
                                new FeatureValue(42, FeatureValueType.NUMBER),
                                3,
                                BigDecimal.ONE)));
        Experiment updated = experimentRepository.findById(experiment.id()).orElseThrow();

        assertThat(result).isEqualTo(ExperimentRepository.ReplaceVariantsResult.UPDATED);
        assertThat(updated.version()).isEqualTo(1L);
        assertThat(updated.variants()).hasSize(2);
        assertThat(updated.variants().getFirst().id()).isEqualTo(keptVariantId);
        assertThat(updated.variants().getFirst().value()).isEqualTo(new FeatureValue(false, FeatureValueType.BOOL));
        assertThat(updated.variants().getFirst().position()).isZero();
        assertThat(updated.variants().get(1).id()).isNotNull();
        assertThat(updated.variants().get(1).id()).isNotEqualTo(deletedVariantId);
        assertThat(updated.variants().get(1).key()).isEqualTo("new-variant");
        assertThat(updated.variants().get(1).position()).isEqualTo(1);
    }

    @Test
    void replaceVariants_shouldReturnVersionConflictAndNotFoundForStaleAndMissingExperiments() {
        String flagKey = "flag-f";
        insertFeatureFlag(flagKey);

        Experiment experiment = new Experiment(
                UUID.randomUUID(),
                flagKey,
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE)),
                ExperimentState.RUNNING,
                0L);
        experimentRepository.save(experiment);

        ExperimentRepository.ReplaceVariantsResult staleResult = experimentRepository.replaceVariants(
                experiment.id(),
                9L,
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(false, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE)));
        ExperimentRepository.ReplaceVariantsResult missingResult = experimentRepository.replaceVariants(
                UUID.randomUUID(),
                0L,
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(false, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE)));

        assertThat(staleResult).isEqualTo(ExperimentRepository.ReplaceVariantsResult.VERSION_CONFLICT);
        assertThat(missingResult).isEqualTo(ExperimentRepository.ReplaceVariantsResult.NOT_FOUND);
    }

    @Test
    void existsDeleteAndFindHelpers_shouldReflectDatabaseState() {
        String flagKey = "flag-g";
        insertFeatureFlag(flagKey);

        Experiment experiment = new Experiment(
                UUID.randomUUID(),
                flagKey,
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE)),
                ExperimentState.APPROVED,
                0L);
        experimentRepository.save(experiment);

        assertThat(experimentRepository.existsById(experiment.id())).isTrue();
        assertThat(experimentRepository.existsByFlagKey(flagKey)).isTrue();
        assertThat(experimentRepository.findFlagKeyById(experiment.id())).contains(flagKey);
        assertThat(experimentRepository.findVariantsByExperimentId(experiment.id()))
                .hasSize(1);
        assertThat(experimentRepository.findVariantsByExperimentIds(List.of(experiment.id())))
                .containsKey(experiment.id());

        int deletedRows = experimentRepository.deleteById(experiment.id());

        assertThat(deletedRows).isEqualTo(1);
        assertThat(experimentRepository.existsById(experiment.id())).isFalse();
        assertThat(experimentRepository.findById(experiment.id())).isEmpty();
        assertThat(experimentRepository.findVariantsByExperimentId(experiment.id()))
                .isEmpty();
    }

    private void insertFeatureFlag(String key) {
        jdbcClient
                .sql("""
                        INSERT INTO feature_flags (id, feature_key, default_value, default_value_type, version)
                        VALUES (:id, :key, :defaultValue, :defaultValueType, :version)
                        """)
                .param("id", UUID.randomUUID())
                .param("key", key)
                .param("defaultValue", "true")
                .param("defaultValueType", "BOOL")
                .param("version", 0L)
                .update();
    }
}
