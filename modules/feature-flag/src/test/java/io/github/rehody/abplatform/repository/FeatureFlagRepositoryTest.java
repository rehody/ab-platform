package io.github.rehody.abplatform.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.repository.rowmapper.FeatureFlagRowMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class FeatureFlagRepositoryTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private FeatureFlagRowMapper rowMapper;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<FeatureFlag> mappedQuerySpec;

    @Mock
    private JdbcClient.ResultQuerySpec resultQuerySpec;

    private FeatureFlagRepository featureFlagRepository;

    @BeforeEach
    void setUp() {
        featureFlagRepository = new FeatureFlagRepository(jdbcClient, rowMapper);
    }

    @Test
    void save_shouldWriteAllParametersAndExecuteInsertUpdate() {
        UUID id = UUID.randomUUID();
        FeatureFlag featureFlag =
                new FeatureFlag(id, "checkout-redesign", new FeatureValue(true, FeatureValueType.BOOL));
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        featureFlagRepository.save(featureFlag);

        verify(jdbcClient).sql(contains("INSERT INTO feature_flags"));
        verify(statementSpec).param("id", id);
        verify(statementSpec).param("key", "checkout-redesign");
        verify(statementSpec).param("defaultValue", true);
        verify(statementSpec).param("defaultValueType", "BOOL");
        verify(statementSpec).update();
    }

    @Test
    void findByKey_shouldReturnMappedFeatureFlagWhenRepositoryContainsRecord() {
        FeatureFlag featureFlag = new FeatureFlag(
                UUID.randomUUID(), "checkout-redesign", new FeatureValue("variant-a", FeatureValueType.STRING));
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("key", "checkout-redesign")).thenReturn(statementSpec);
        when(statementSpec.query(rowMapper)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.of(featureFlag));

        Optional<FeatureFlag> response = featureFlagRepository.findByKey("checkout-redesign");

        assertThat(response).contains(featureFlag);
        verify(jdbcClient).sql(contains("SELECT id, feature_key, default_value, default_value_type"));
    }

    @Test
    void update_shouldWriteKeyValueAndTypeAndExecuteUpdateStatement() {
        FeatureValue defaultValue = new FeatureValue(100, FeatureValueType.NUMBER);
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        featureFlagRepository.update("checkout-redesign", defaultValue);

        verify(jdbcClient).sql(contains("UPDATE feature_flags"));
        verify(statementSpec).param("key", "checkout-redesign");
        verify(statementSpec).param("defaultValue", 100);
        verify(statementSpec).param("defaultValueType", "NUMBER");
        verify(statementSpec).update();
    }

    @Test
    void existsByKey_shouldReturnTrueWhenJdbcReturnsTrueBooleanValue() {
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("key", "flag-a")).thenReturn(statementSpec);
        when(statementSpec.query()).thenReturn(resultQuerySpec);
        when(resultQuerySpec.singleValue()).thenReturn(Boolean.TRUE);

        boolean response = featureFlagRepository.existsByKey("flag-a");

        assertThat(response).isTrue();
        verify(jdbcClient).sql(contains("SELECT EXISTS"));
    }

    @Test
    void existsByKey_shouldReturnFalseWhenJdbcReturnsNullOrFalseValue() {
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("key", "flag-b")).thenReturn(statementSpec);
        when(statementSpec.query()).thenReturn(resultQuerySpec);
        when(resultQuerySpec.singleValue()).thenReturn(null);

        boolean nullResponse = featureFlagRepository.existsByKey("flag-b");

        when(statementSpec.param("key", "flag-c")).thenReturn(statementSpec);
        when(resultQuerySpec.singleValue()).thenReturn(Boolean.FALSE);

        boolean falseResponse = featureFlagRepository.existsByKey("flag-c");

        assertThat(nullResponse).isFalse();
        assertThat(falseResponse).isFalse();
    }
}
