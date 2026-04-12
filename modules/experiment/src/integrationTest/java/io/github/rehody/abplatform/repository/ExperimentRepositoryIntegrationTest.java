package io.github.rehody.abplatform.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.config.AbstractIntegrationDatabaseTest;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.repository.jdbc.ExperimentJdbcRepository;
import io.github.rehody.abplatform.repository.jdbc.ExperimentVariantJdbcRepository;
import io.github.rehody.abplatform.repository.mapper.ExperimentAggregateMapper;
import io.github.rehody.abplatform.repository.rowmapper.ExperimentRowMapper;
import io.github.rehody.abplatform.repository.rowmapper.ExperimentVariantRowMapper;
import io.github.rehody.abplatform.repository.sync.ExperimentVariantSynchronizer;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Import({
    ExperimentRepository.class,
    ExperimentJdbcRepository.class,
    ExperimentVariantJdbcRepository.class,
    ExperimentAggregateMapper.class,
    ExperimentVariantSynchronizer.class,
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
                        CREATE TABLE IF NOT EXISTS experiment_domains (
                            key VARCHAR(64) PRIMARY KEY,
                            name VARCHAR(255) NOT NULL
                        )
                        """).update();

        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS experiments (
                            id UUID PRIMARY KEY,
                            flag_key VARCHAR(255) NOT NULL REFERENCES feature_flags (feature_key),
                            domain_key VARCHAR(64) NOT NULL REFERENCES experiment_domains (key),
                            regular_rollout_percentage INT NOT NULL,
                            after_rollback BOOLEAN NOT NULL,
                            still_negative_after_rollback BOOLEAN NOT NULL,
                            state VARCHAR(16) NOT NULL,
                            version BIGINT NOT NULL DEFAULT 0,
                            started_at TIMESTAMPTZ NULL,
                            completed_at TIMESTAMPTZ NULL,
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
                            weight NUMERIC NULL,
                            variant_type VARCHAR(16) NOT NULL CHECK (variant_type IN ('CONTROL', 'REGULAR')),
                            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                            updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                            UNIQUE (experiment_id, key),
                            UNIQUE (experiment_id, position),
                            UNIQUE (experiment_id, value, value_type),
                            CHECK (
                                (variant_type = 'CONTROL' AND key = 'control')
                                OR (variant_type = 'REGULAR' AND key <> 'control')
                            ),
                            CHECK (
                                (variant_type = 'CONTROL' AND weight IS NULL)
                                OR (variant_type = 'REGULAR' AND weight IS NOT NULL AND weight > 0)
                            )
                        )
                        """).update();
        jdbcClient.sql("""
                        CREATE UNIQUE INDEX IF NOT EXISTS experiment_variants_single_control_per_experiment_idx
                        ON experiment_variants (experiment_id)
                        WHERE variant_type = 'CONTROL'
                        """).update();

        jdbcClient.sql("DELETE FROM experiment_variants").update();
        jdbcClient.sql("DELETE FROM experiments").update();
        jdbcClient.sql("DELETE FROM experiment_domains").update();
        jdbcClient.sql("DELETE FROM feature_flags").update();

        insertDomain("CORE", "Core");
    }

    @Test
    void saveAndFindById_shouldPersistExperimentAndVariantsAsProvided() {
        String flagKey = "checkout-redesign";
        insertFeatureFlag(flagKey);

        UUID experimentId = UUID.randomUUID();
        UUID controlVariantId = UUID.randomUUID();
        UUID regularVariantId = UUID.randomUUID();
        Experiment experiment = new Experiment(
                experimentId,
                flagKey,
                "CORE",
                ExperimentRolloutPlan.initial(),
                List.of(
                        controlVariant(controlVariantId, new FeatureValue(true, FeatureValueType.BOOL), 0),
                        regularVariant(
                                regularVariantId,
                                "variant-a",
                                new FeatureValue("blue", FeatureValueType.STRING),
                                1,
                                BigDecimal.ONE)),
                ExperimentState.DRAFT,
                0L,
                null,
                null);

        experimentRepository.save(experiment);
        Experiment loaded = experimentRepository.findById(experimentId).orElseThrow();

        assertThat(loaded.id()).isEqualTo(experimentId);
        assertThat(loaded.flagKey()).isEqualTo(flagKey);
        assertThat(loaded.domainKey()).isEqualTo("CORE");
        assertThat(loaded.state()).isEqualTo(ExperimentState.DRAFT);
        assertThat(loaded.version()).isZero();
        assertThat(loaded.variants()).hasSize(2);
        assertThat(loaded.variants().getFirst().id()).isEqualTo(controlVariantId);
        assertThat(loaded.variants().getFirst().key()).isEqualTo("control");
        assertThat(loaded.variants().getFirst().position()).isZero();
        assertThat(loaded.variants().get(1).id()).isEqualTo(regularVariantId);
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
                "CORE",
                ExperimentRolloutPlan.initial(),
                List.of(controlVariant(UUID.randomUUID(), new FeatureValue(true, FeatureValueType.BOOL), 0)),
                ExperimentState.RUNNING,
                0L,
                null,
                null);
        Experiment second = new Experiment(
                UUID.randomUUID(),
                secondFlagKey,
                "CORE",
                ExperimentRolloutPlan.initial(),
                List.of(regularVariant(
                        UUID.randomUUID(),
                        "variant-b",
                        new FeatureValue(10, FeatureValueType.NUMBER),
                        0,
                        BigDecimal.ONE)),
                ExperimentState.APPROVED,
                0L,
                null,
                null);

        experimentRepository.save(first);
        experimentRepository.save(second);

        Experiment byFlagKey = experimentRepository.findByFlagKey(secondFlagKey).orElseThrow();
        List<Experiment> all = experimentRepository.findAll().stream()
                .sorted(Comparator.comparing(Experiment::flagKey))
                .toList();

        assertThat(byFlagKey.id()).isEqualTo(second.id());
        assertThat(byFlagKey.domainKey()).isEqualTo("CORE");
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
                "CORE",
                ExperimentRolloutPlan.initial(),
                List.of(controlVariant(UUID.randomUUID(), new FeatureValue(true, FeatureValueType.BOOL), 0)),
                ExperimentState.DRAFT,
                0L,
                null,
                null);
        experimentRepository.save(initial);

        ExperimentRepository.UpdateOutcome result = experimentRepository.update(new Experiment(
                initial.id(),
                flagKey,
                "CORE",
                initial.rolloutPlan(),
                initial.variants(),
                ExperimentState.RUNNING,
                0L,
                null,
                null));
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
                "CORE",
                ExperimentRolloutPlan.initial(),
                List.of(controlVariant(UUID.randomUUID(), new FeatureValue(true, FeatureValueType.BOOL), 0)),
                ExperimentState.DRAFT,
                0L,
                null,
                null);
        experimentRepository.save(persisted);

        ExperimentRepository.UpdateOutcome staleResult = experimentRepository.update(new Experiment(
                persisted.id(),
                flagKey,
                "CORE",
                persisted.rolloutPlan(),
                persisted.variants(),
                ExperimentState.APPROVED,
                9L,
                null,
                null));
        ExperimentRepository.UpdateOutcome missingResult = experimentRepository.update(new Experiment(
                UUID.randomUUID(),
                flagKey,
                "CORE",
                ExperimentRolloutPlan.initial(),
                List.of(),
                ExperimentState.APPROVED,
                0L,
                null,
                null));

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
                "CORE",
                ExperimentRolloutPlan.initial(),
                List.of(
                        controlVariant(keptVariantId, new FeatureValue(true, FeatureValueType.BOOL), 0),
                        regularVariant(
                                deletedVariantId,
                                "old",
                                new FeatureValue("old", FeatureValueType.STRING),
                                1,
                                BigDecimal.ONE)),
                ExperimentState.RUNNING,
                0L,
                null,
                null);
        experimentRepository.save(experiment);

        ExperimentRepository.ReplaceVariantsResult result = experimentRepository.replaceVariants(
                experiment.id(),
                0L,
                List.of(
                        controlVariant(keptVariantId, new FeatureValue(false, FeatureValueType.BOOL), 0),
                        regularVariant(
                                UUID.randomUUID(),
                                "new-variant",
                                new FeatureValue(42, FeatureValueType.NUMBER),
                                1,
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
                "CORE",
                ExperimentRolloutPlan.initial(),
                List.of(controlVariant(UUID.randomUUID(), new FeatureValue(true, FeatureValueType.BOOL), 0)),
                ExperimentState.RUNNING,
                0L,
                null,
                null);
        experimentRepository.save(experiment);

        ExperimentRepository.ReplaceVariantsResult staleResult = experimentRepository.replaceVariants(
                experiment.id(),
                9L,
                List.of(controlVariant(UUID.randomUUID(), new FeatureValue(false, FeatureValueType.BOOL), 0)));
        ExperimentRepository.ReplaceVariantsResult missingResult = experimentRepository.replaceVariants(
                UUID.randomUUID(),
                0L,
                List.of(controlVariant(UUID.randomUUID(), new FeatureValue(false, FeatureValueType.BOOL), 0)));

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
                "CORE",
                ExperimentRolloutPlan.initial(),
                List.of(controlVariant(UUID.randomUUID(), new FeatureValue(true, FeatureValueType.BOOL), 0)),
                ExperimentState.APPROVED,
                0L,
                null,
                null);
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

    private void insertDomain(String key, String name) {
        jdbcClient.sql("""
                        INSERT INTO experiment_domains (key, name)
                        VALUES (:key, :name)
                        """).param("key", key).param("name", name).update();
    }

    private ExperimentVariant controlVariant(UUID id, FeatureValue value, int position) {
        return new ExperimentVariant(id, "control", value, position, null, ExperimentVariantType.CONTROL);
    }

    private ExperimentVariant regularVariant(UUID id, String key, FeatureValue value, int position, BigDecimal weight) {
        return new ExperimentVariant(id, key, value, position, weight, ExperimentVariantType.REGULAR);
    }
}
