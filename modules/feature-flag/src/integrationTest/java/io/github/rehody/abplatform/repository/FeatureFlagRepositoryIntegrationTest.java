package io.github.rehody.abplatform.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.config.AbstractIntegrationDatabaseTest;
import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Import({FeatureFlagRepository.class})
class FeatureFlagRepositoryIntegrationTest extends AbstractIntegrationDatabaseTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private FeatureFlagRepository featureFlagRepository;

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
        jdbcClient.sql("DELETE FROM feature_flags").update();
    }

    @Test
    void saveAndFindByKey_shouldPersistAndReturnFeatureFlagWhenRecordExists() {
        FeatureFlag featureFlag = new FeatureFlag(
                UUID.randomUUID(), "checkout-redesign", new FeatureValue("variant-a", FeatureValueType.STRING), 0L);

        featureFlagRepository.save(featureFlag);
        Optional<FeatureFlag> loaded = featureFlagRepository.findByKey("checkout-redesign");

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow()).isEqualTo(featureFlag);
    }

    @Test
    void findByKey_shouldReturnEmptyAndHandleUnknownKey() {
        Optional<FeatureFlag> loaded = featureFlagRepository.findByKey("missing-flag");

        assertThat(loaded).isEmpty();
    }

    @Test
    void update_shouldPersistNewDefaultValueAndTypeWhenFeatureFlagExists() {
        FeatureFlag initial = new FeatureFlag(
                UUID.randomUUID(), "checkout-redesign", new FeatureValue(true, FeatureValueType.BOOL), 0L);
        featureFlagRepository.save(initial);

        int updatedRows =
                featureFlagRepository.update("checkout-redesign", new FeatureValue(100, FeatureValueType.NUMBER), 0L);
        FeatureFlag updated =
                featureFlagRepository.findByKey("checkout-redesign").orElseThrow();

        assertThat(updatedRows).isEqualTo(1);
        assertThat(updated.defaultValue().value()).isEqualTo("100");
        assertThat(updated.defaultValue().type()).isEqualTo(FeatureValueType.NUMBER);
        assertThat(updated.version()).isEqualTo(1L);
    }

    @Test
    void update_shouldReturnZeroAndSkipWriteWhenVersionDoesNotMatch() {
        FeatureFlag initial = new FeatureFlag(
                UUID.randomUUID(), "checkout-redesign", new FeatureValue(true, FeatureValueType.BOOL), 0L);
        featureFlagRepository.save(initial);

        int updatedRows =
                featureFlagRepository.update("checkout-redesign", new FeatureValue(false, FeatureValueType.BOOL), 9L);
        FeatureFlag loaded =
                featureFlagRepository.findByKey("checkout-redesign").orElseThrow();

        assertThat(updatedRows).isZero();
        assertThat(loaded.defaultValue().value()).isEqualTo("true");
        assertThat(loaded.version()).isEqualTo(0L);
    }

    @Test
    void existsByKey_shouldReturnTrueAndFalseForExistingAndMissingKeys() {
        FeatureFlag featureFlag =
                new FeatureFlag(UUID.randomUUID(), "beta-banner", new FeatureValue(false, FeatureValueType.BOOL), 0L);
        featureFlagRepository.save(featureFlag);

        boolean existing = featureFlagRepository.existsByKey("beta-banner");
        boolean missing = featureFlagRepository.existsByKey("other");

        assertThat(existing).isTrue();
        assertThat(missing).isFalse();
    }

    @Test
    void save_shouldThrowDataIntegrityViolationExceptionAndRejectDuplicateKey() {
        FeatureFlag first =
                new FeatureFlag(UUID.randomUUID(), "dup-flag", new FeatureValue("v1", FeatureValueType.STRING), 0L);
        FeatureFlag duplicate =
                new FeatureFlag(UUID.randomUUID(), "dup-flag", new FeatureValue("v2", FeatureValueType.STRING), 0L);

        featureFlagRepository.save(first);

        assertThatThrownBy(() -> featureFlagRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
