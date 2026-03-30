package io.github.rehody.abplatform.metric.repository.rowmapper;

import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class MetricDefinitionRowMapper implements RowMapper<MetricDefinition> {

    @Override
    public MetricDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new MetricDefinition(
                rs.getObject("id", UUID.class),
                rs.getString("key"),
                rs.getString("name"),
                MetricType.valueOf(rs.getString("type")));
    }
}
