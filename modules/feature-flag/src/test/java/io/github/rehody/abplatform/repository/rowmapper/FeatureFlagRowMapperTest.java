package io.github.rehody.abplatform.repository.rowmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.FeatureFlag;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureFlagRowMapperTest {

    @Mock
    private ResultSet resultSet;

    private final FeatureFlagRowMapper rowMapper = new FeatureFlagRowMapper();

    @Test
    void mapRow_shouldMapAllColumnsAndCreateFeatureFlag() throws SQLException {
        UUID id = UUID.randomUUID();

        when(resultSet.getObject("id", UUID.class)).thenReturn(id);
        when(resultSet.getString("feature_key")).thenReturn("checkout-redesign");
        when(resultSet.getObject("default_value")).thenReturn("variant-a");
        when(resultSet.getString("default_value_type")).thenReturn("STRING");
        when(resultSet.getLong("version")).thenReturn(5L);

        FeatureFlag featureFlag = rowMapper.mapRow(resultSet, 0);

        assertThat(Objects.requireNonNull(featureFlag).id()).isEqualTo(id);
        assertThat(featureFlag.key()).isEqualTo("checkout-redesign");
        assertThat(featureFlag.defaultValue().value()).isEqualTo("variant-a");
        assertThat(featureFlag.defaultValue().type()).isEqualTo(FeatureValueType.STRING);
        assertThat(featureFlag.version()).isEqualTo(5L);
    }
}
