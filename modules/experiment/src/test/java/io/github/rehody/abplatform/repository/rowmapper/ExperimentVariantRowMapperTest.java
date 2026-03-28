package io.github.rehody.abplatform.repository.rowmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentVariantRowMapperTest {

    @Mock
    private ResultSet resultSet;

    private final ExperimentVariantRowMapper rowMapper = new ExperimentVariantRowMapper();

    @Test
    void mapRow_shouldMapAllColumnsAndCreateExperimentVariant() throws SQLException {
        UUID id = UUID.randomUUID();

        when(resultSet.getObject("id", UUID.class)).thenReturn(id);
        when(resultSet.getString("key")).thenReturn("control");
        when(resultSet.getObject("value")).thenReturn("variant-a");
        when(resultSet.getString("value_type")).thenReturn("STRING");
        when(resultSet.getInt("position")).thenReturn(2);
        when(resultSet.getObject("weight", BigDecimal.class)).thenReturn(BigDecimal.valueOf(25));

        ExperimentVariant variant = rowMapper.mapRow(resultSet, 0);

        assertThat(variant.id()).isEqualTo(id);
        assertThat(variant.key()).isEqualTo("control");
        assertThat(variant.value().value()).isEqualTo("variant-a");
        assertThat(variant.value().type()).isEqualTo(FeatureValueType.STRING);
        assertThat(variant.position()).isEqualTo(2);
        assertThat(variant.weight()).isEqualByComparingTo("25");
    }
}
