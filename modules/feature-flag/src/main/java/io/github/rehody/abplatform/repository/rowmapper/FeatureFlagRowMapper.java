package io.github.rehody.abplatform.repository.rowmapper;

import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagRowMapper implements RowMapper<FeatureFlag> {

    @Override
    public FeatureFlag mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new FeatureFlag(
                rs.getObject("id", UUID.class),
                rs.getString("feature_key"),
                new FeatureValue(
                        rs.getObject("default_value"),
                        FeatureValue.FeatureValueType.valueOf(rs.getString("default_value_type"))));
    }
}
