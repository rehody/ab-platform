package io.github.rehody.abplatform.repository.rowmapper;

import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class ExperimentVariantRowMapper implements RowMapper<ExperimentVariant> {

    @Override
    public ExperimentVariant mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ExperimentVariant(
                rs.getObject("id", UUID.class),
                rs.getString("key"),
                new FeatureValue(
                        rs.getObject("value"), FeatureValue.FeatureValueType.valueOf(rs.getString("value_type"))),
                rs.getInt("position"));
    }
}
