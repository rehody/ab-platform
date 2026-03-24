package io.github.rehody.abplatform.repository;

import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.repository.rowmapper.FeatureFlagRowMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
@Repository
@RequiredArgsConstructor
public class FeatureFlagRepository {

    private static final String INSERT_FLAG_SQL = """
        INSERT INTO feature_flag (id, feature_key, default_value, default_value_type)
        VALUES (:id, :key, :defaultValue, :defaultValueType)
        """;

    private static final String SELECT_FLAG_BY_KEY_SQL = """
        SELECT id, feature_key, default_value, default_value_type
        FROM feature_flag
        WHERE feature_key = :key
        """;

    private static final String UPDATE_FLAG_DEFAULT_VALUE_BY_KEY_SQL = """
        UPDATE feature_flag
        SET default_value = :defaultValue, default_value_type = :defaultValueType
        WHERE feature_key = :key
        """;

    private static final String EXISTS_BY_KEY_SQL = """
        SELECT EXISTS(
            SELECT 1 FROM feature_flag WHERE feature_key = :key
        )
        """;

    private final JdbcClient jdbcClient;
    private final FeatureFlagRowMapper rowMapper;

    public void save(FeatureFlag featureFlag) {
        jdbcClient
                .sql(INSERT_FLAG_SQL)
                .param("id", featureFlag.id())
                .param("key", featureFlag.key())
                .param("defaultValue", featureFlag.defaultValue().value())
                .param("defaultValueType", featureFlag.defaultValue().type().name())
                .update();
    }

    public Optional<FeatureFlag> findByKey(String key) {
        return jdbcClient
                .sql(SELECT_FLAG_BY_KEY_SQL)
                .param("key", key)
                .query(rowMapper)
                .optional();
    }

    public boolean update(String key, FeatureValue defaultValue) {
        int affectedRows = jdbcClient
                .sql(UPDATE_FLAG_DEFAULT_VALUE_BY_KEY_SQL)
                .param("key", key)
                .param("defaultValue", defaultValue.value())
                .param("defaultValueType", defaultValue.type().name())
                .update();

        return affectedRows > 0;
    }

    public boolean existsByKey(String key) {
        return Boolean.TRUE.equals(
                jdbcClient.sql(EXISTS_BY_KEY_SQL).param("key", key).query().singleValue());
    }
}
