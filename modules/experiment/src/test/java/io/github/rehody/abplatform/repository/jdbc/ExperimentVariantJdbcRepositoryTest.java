package io.github.rehody.abplatform.repository.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.repository.rowmapper.ExperimentVariantRowMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class ExperimentVariantJdbcRepositoryTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private ExperimentVariantRowMapper experimentVariantRowMapper;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec mappedQuerySpec;

    private ExperimentVariantJdbcRepository experimentVariantJdbcRepository;

    @BeforeEach
    void setUp() {
        experimentVariantJdbcRepository =
                new ExperimentVariantJdbcRepository(jdbcClient, namedParameterJdbcTemplate, experimentVariantRowMapper);
    }

    @Test
    void findByExperimentId_shouldReturnVariantsForExperiment() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants = List.of(variant("control"));
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("experimentId", experimentId)).thenReturn(statementSpec);
        when(statementSpec.query(experimentVariantRowMapper)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.list()).thenReturn(variants);

        List<ExperimentVariant> result = experimentVariantJdbcRepository.findByExperimentId(experimentId);

        assertThat(result).isEqualTo(variants);
        verify(jdbcClient).sql(contains("SELECT id, experiment_id, key, value, value_type, position"));
    }

    @Test
    void findByExperimentIds_shouldReturnEmptyMapWhenInputIdsAreNullOrEmpty() {
        assertThat(experimentVariantJdbcRepository.findByExperimentIds(null)).isEmpty();
        assertThat(experimentVariantJdbcRepository.findByExperimentIds(List.of()))
                .isEmpty();
    }

    @Test
    void findByExperimentIds_shouldGroupVariantsByExperimentId() throws Exception {
        UUID firstExperimentId = UUID.randomUUID();
        UUID secondExperimentId = UUID.randomUUID();
        ExperimentVariant firstVariant = variant("control");
        ExperimentVariant secondVariant = variant("variant-a");
        ExperimentVariant thirdVariant = variant("variant-b");
        ResultSet firstResultSet = resultSetWithExperimentId(firstExperimentId);
        ResultSet secondResultSet = resultSetWithExperimentId(firstExperimentId);
        ResultSet thirdResultSet = resultSetWithExperimentId(secondExperimentId);
        AtomicReference<RowMapper<?>> rowMapperRef = new AtomicReference<>();
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("experimentIds", List.of(firstExperimentId, secondExperimentId)))
                .thenReturn(statementSpec);
        when(statementSpec.query(any(RowMapper.class))).thenAnswer(invocation -> {
            rowMapperRef.set(invocation.getArgument(0));
            return mappedQuerySpec;
        });
        when(experimentVariantRowMapper.mapRow(firstResultSet, 0)).thenReturn(firstVariant);
        when(experimentVariantRowMapper.mapRow(secondResultSet, 1)).thenReturn(secondVariant);
        when(experimentVariantRowMapper.mapRow(thirdResultSet, 2)).thenReturn(thirdVariant);
        when(mappedQuerySpec.list())
                .thenAnswer(invocation -> List.of(
                        rowMapperRef.get().mapRow(firstResultSet, 0),
                        rowMapperRef.get().mapRow(secondResultSet, 1),
                        rowMapperRef.get().mapRow(thirdResultSet, 2)));

        Map<UUID, List<ExperimentVariant>> result =
                experimentVariantJdbcRepository.findByExperimentIds(List.of(firstExperimentId, secondExperimentId));

        assertThat(result)
                .containsEntry(firstExperimentId, List.of(firstVariant, secondVariant))
                .containsEntry(secondExperimentId, List.of(thirdVariant));
    }

    @Test
    void findIdsByExperimentId_shouldReturnVariantIds() throws Exception {
        UUID experimentId = UUID.randomUUID();
        List<UUID> variantIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        ResultSet firstResultSet = resultSetWithVariantId(variantIds.get(0));
        ResultSet secondResultSet = resultSetWithVariantId(variantIds.get(1));
        AtomicReference<RowMapper<?>> rowMapperRef = new AtomicReference<>();
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("experimentId", experimentId)).thenReturn(statementSpec);
        when(statementSpec.query(any(RowMapper.class))).thenAnswer(invocation -> {
            rowMapperRef.set(invocation.getArgument(0));
            return mappedQuerySpec;
        });
        when(mappedQuerySpec.list())
                .thenAnswer(invocation -> List.of(
                        rowMapperRef.get().mapRow(firstResultSet, 0),
                        rowMapperRef.get().mapRow(secondResultSet, 1)));

        assertThat(experimentVariantJdbcRepository.findIdsByExperimentId(experimentId))
                .isEqualTo(variantIds);
    }

    @Test
    void batchInsert_shouldSkipJdbcCallWhenVariantsEmpty() {
        experimentVariantJdbcRepository.batchInsert(UUID.randomUUID(), List.of());

        verify(namedParameterJdbcTemplate, never())
                .batchUpdate(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class));
    }

    @Test
    void batchInsert_shouldExecuteBatchUpdateWhenVariantsPresent() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants = List.of(variant("control"));
        when(namedParameterJdbcTemplate.batchUpdate(
                        anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class)))
                .thenReturn(new int[] {1});

        experimentVariantJdbcRepository.batchInsert(experimentId, variants);

        verify(namedParameterJdbcTemplate)
                .batchUpdate(
                        contains("INSERT INTO experiment_variants"),
                        any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class));
    }

    @Test
    void batchUpdate_shouldSkipJdbcCallWhenVariantsEmpty() {
        experimentVariantJdbcRepository.batchUpdate(UUID.randomUUID(), List.of());

        verify(namedParameterJdbcTemplate, never())
                .batchUpdate(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class));
    }

    @Test
    void batchUpdate_shouldExecuteBatchUpdateWhenVariantsPresent() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants = List.of(variant("control"));
        when(namedParameterJdbcTemplate.batchUpdate(
                        anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class)))
                .thenReturn(new int[] {1});

        experimentVariantJdbcRepository.batchUpdate(experimentId, variants);

        verify(namedParameterJdbcTemplate)
                .batchUpdate(
                        contains("UPDATE experiment_variants"),
                        any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class));
    }

    @Test
    void batchUpdate_shouldThrowIllegalStateExceptionWhenAffectedRowsAreUnexpected() {
        UUID experimentId = UUID.randomUUID();
        List<ExperimentVariant> variants = List.of(variant("control"));
        when(namedParameterJdbcTemplate.batchUpdate(
                        anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class)))
                .thenReturn(new int[] {0});

        assertThatThrownBy(() -> experimentVariantJdbcRepository.batchUpdate(experimentId, variants))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected 1 updated row for variant");
    }

    @Test
    void batchDelete_shouldSkipJdbcCallWhenVariantIdsEmpty() {
        experimentVariantJdbcRepository.batchDelete(UUID.randomUUID(), List.of());

        verify(namedParameterJdbcTemplate, never())
                .batchUpdate(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class));
    }

    @Test
    void batchDelete_shouldThrowIllegalStateExceptionWhenAffectedRowsAreUnexpected() {
        UUID experimentId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(namedParameterJdbcTemplate.batchUpdate(
                        anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class)))
                .thenReturn(new int[] {0});

        assertThatThrownBy(() -> experimentVariantJdbcRepository.batchDelete(experimentId, List.of(variantId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected 1 deleted row for variant");
    }

    @Test
    void batchDelete_shouldExecuteBatchUpdateWhenVariantIdsPresent() {
        UUID experimentId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(namedParameterJdbcTemplate.batchUpdate(
                        anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class)))
                .thenReturn(new int[] {1});

        experimentVariantJdbcRepository.batchDelete(experimentId, List.of(variantId));

        verify(namedParameterJdbcTemplate)
                .batchUpdate(
                        contains("DELETE FROM experiment_variants"),
                        any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class));
    }

    private ExperimentVariant variant(String key) {
        return new ExperimentVariant(
                UUID.randomUUID(), key, new FeatureValue(true, FeatureValueType.BOOL), 0, BigDecimal.ONE);
    }

    private ResultSet resultSetWithExperimentId(UUID experimentId) throws Exception {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getObject("experiment_id", UUID.class)).thenReturn(experimentId);
        return resultSet;
    }

    private ResultSet resultSetWithVariantId(UUID variantId) throws Exception {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getObject("id", UUID.class)).thenReturn(variantId);
        return resultSet;
    }
}
