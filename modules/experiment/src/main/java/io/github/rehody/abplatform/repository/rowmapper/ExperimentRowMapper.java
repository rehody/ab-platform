package io.github.rehody.abplatform.repository.rowmapper;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class ExperimentRowMapper implements RowMapper<Experiment> {

    @Override
    public Experiment mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Experiment(
                rs.getObject("id", UUID.class),
                rs.getString("flag_key"),
                List.of(),
                ExperimentState.valueOf(rs.getString("state")),
                rs.getLong("version"));
    }
}
