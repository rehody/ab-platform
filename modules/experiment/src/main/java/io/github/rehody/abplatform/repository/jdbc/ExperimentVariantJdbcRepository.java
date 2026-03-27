package io.github.rehody.abplatform.repository.jdbc;

import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.repository.rowmapper.ExperimentVariantRowMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExperimentVariantJdbcRepository {

    private static final String SELECT_VARIANTS_BY_EXPERIMENT_ID_SQL = """
        SELECT id, experiment_id, key, value, value_type, position
        FROM experiment_variants
        WHERE experiment_id = :experimentId
        ORDER BY position, id
        """;

    private static final String SELECT_VARIANTS_BY_EXPERIMENT_IDS_SQL = """
        SELECT id, experiment_id, key, value, value_type, position
        FROM experiment_variants
        WHERE experiment_id IN (:experimentIds)
        ORDER BY experiment_id, position, id
        """;

    private static final String INSERT_VARIANT_SQL = """
        INSERT INTO experiment_variants (
            id,
            experiment_id,
            key,
            value,
            value_type,
            position
        )
        VALUES (
            :id,
            :experimentId,
            :key,
            :value,
            :valueType,
            :position
        )
        """;

    private static final String UPDATE_VARIANT_SQL = """
        UPDATE experiment_variants
        SET key = :key,
            value = :value,
            value_type = :valueType,
            position = :position
        WHERE id = :id
          AND experiment_id = :experimentId
        """;

    private static final String DELETE_VARIANT_BY_ID_SQL = """
        DELETE FROM experiment_variants
        WHERE id = :id
          AND experiment_id = :experimentId
        """;

    private static final String SELECT_VARIANT_IDS_BY_EXPERIMENT_ID_SQL = """
        SELECT id
        FROM experiment_variants
        WHERE experiment_id = :experimentId
        """;

    private final JdbcClient jdbcClient;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ExperimentVariantRowMapper experimentVariantRowMapper;

    public List<ExperimentVariant> findByExperimentId(UUID experimentId) {
        return jdbcClient
                .sql(SELECT_VARIANTS_BY_EXPERIMENT_ID_SQL)
                .param("experimentId", experimentId)
                .query(experimentVariantRowMapper)
                .list();
    }

    public Map<UUID, List<ExperimentVariant>> findByExperimentIds(List<UUID> experimentIds) {
        if (experimentIds == null || experimentIds.isEmpty()) {
            return Map.of();
        }

        List<ExperimentVariantRow> rows = jdbcClient
                .sql(SELECT_VARIANTS_BY_EXPERIMENT_IDS_SQL)
                .param("experimentIds", experimentIds)
                .query((rs, rowNum) -> new ExperimentVariantRow(
                        rs.getObject("experiment_id", UUID.class), experimentVariantRowMapper.mapRow(rs, rowNum)))
                .list();

        return rows.stream()
                .collect(Collectors.groupingBy(
                        ExperimentVariantRow::experimentId,
                        LinkedHashMap::new,
                        Collectors.mapping(ExperimentVariantRow::variant, Collectors.toList())));
    }

    public List<UUID> findIdsByExperimentId(UUID experimentId) {
        return jdbcClient
                .sql(SELECT_VARIANT_IDS_BY_EXPERIMENT_ID_SQL)
                .param("experimentId", experimentId)
                .query((rs, rowNum) -> rs.getObject("id", UUID.class))
                .list();
    }

    public void batchInsert(UUID experimentId, List<ExperimentVariant> variants) {
        if (variants.isEmpty()) {
            return;
        }

        SqlParameterSource[] batchParams = toVariantBatchParams(experimentId, variants);
        int[] affectedRows = namedParameterJdbcTemplate.batchUpdate(INSERT_VARIANT_SQL, batchParams);
        assertBatchSingleRows(affectedRows, "inserted", experimentId, toVariantIds(variants));
    }

    public void batchUpdate(UUID experimentId, List<ExperimentVariant> variants) {
        if (variants.isEmpty()) {
            return;
        }

        SqlParameterSource[] batchParams = toVariantBatchParams(experimentId, variants);
        int[] affectedRows = namedParameterJdbcTemplate.batchUpdate(UPDATE_VARIANT_SQL, batchParams);
        assertBatchSingleRows(affectedRows, "updated", experimentId, toVariantIds(variants));
    }

    public void batchDelete(UUID experimentId, List<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return;
        }

        SqlParameterSource[] batchParams = variantIds.stream()
                .map(variantId ->
                        new MapSqlParameterSource().addValue("id", variantId).addValue("experimentId", experimentId))
                .toArray(SqlParameterSource[]::new);

        int[] affectedRows = namedParameterJdbcTemplate.batchUpdate(DELETE_VARIANT_BY_ID_SQL, batchParams);
        assertBatchSingleRows(affectedRows, "deleted", experimentId, variantIds);
    }

    private void assertBatchSingleRows(int[] affectedRows, String operation, UUID experimentId, List<UUID> variantIds) {
        for (int index = 0; index < affectedRows.length; index++) {
            if (affectedRows[index] != 1) {
                throw new IllegalStateException("Expected 1 %s row for variant %s in experiment %s, but got %d"
                        .formatted(operation, variantIds.get(index), experimentId, affectedRows[index]));
            }
        }
    }

    private SqlParameterSource[] toVariantBatchParams(UUID experimentId, List<ExperimentVariant> variants) {
        return variants.stream()
                .map(variant -> new MapSqlParameterSource()
                        .addValue("id", variant.id())
                        .addValue("experimentId", experimentId)
                        .addValue("key", variant.key())
                        .addValue("value", variant.value().value())
                        .addValue("valueType", variant.value().type().name())
                        .addValue("position", variant.position()))
                .toArray(SqlParameterSource[]::new);
    }

    private List<UUID> toVariantIds(List<ExperimentVariant> variants) {
        return variants.stream().map(ExperimentVariant::id).toList();
    }

    private record ExperimentVariantRow(UUID experimentId, ExperimentVariant variant) {}
}
