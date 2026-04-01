package io.github.rehody.abplatform.repository.rowmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentRowMapperTest {

    @Mock
    private ResultSet resultSet;

    private final ExperimentRowMapper rowMapper = new ExperimentRowMapper();

    @Test
    void mapRow_shouldMapAllColumnsAndCreateExperiment() throws SQLException {
        UUID id = UUID.randomUUID();

        when(resultSet.getObject("id", UUID.class)).thenReturn(id);
        when(resultSet.getString("flag_key")).thenReturn("checkout-redesign");
        when(resultSet.getString("state")).thenReturn("RUNNING");
        when(resultSet.getLong("version")).thenReturn(5L);

        Experiment experiment = rowMapper.mapRow(resultSet, 0);

        assertThat(experiment.id()).isEqualTo(id);
        assertThat(experiment.flagKey()).isEqualTo("checkout-redesign");
        assertThat(experiment.variants()).isEmpty();
        assertThat(experiment.state()).isEqualTo(ExperimentState.RUNNING);
        assertThat(experiment.version()).isEqualTo(5L);
    }
}
