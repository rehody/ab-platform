package io.github.rehody.abplatform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.dto.response.ExperimentResponse;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ExperimentResponseCacheCodecTest {

    private final ExperimentResponseCacheCodec codec = new ExperimentResponseCacheCodec(new ObjectMapper());

    @Test
    void write_shouldSerializeResponseAndPreserveFields() {
        ExperimentResponse response = new ExperimentResponse(
                "flag-a",
                List.of(new ExperimentVariant(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0)),
                ExperimentState.RUNNING,
                6L);

        String json = codec.write(response);

        assertThat(json).contains("\"flagKey\":\"flag-a\"");
        assertThat(json).contains("\"key\":\"control\"");
        assertThat(json).contains("\"type\":\"BOOL\"");
        assertThat(json).contains("\"version\":6");
    }

    @Test
    void read_shouldDeserializeResponseAndRestoreFields() {
        String json = """
                {"flagKey":"flag-b","variants":[{"id":"11111111-1111-1111-1111-111111111111","key":"variant-a","value":{"value":123,"type":"NUMBER"},"position":1}],"state":"APPROVED","version":4}
                """;

        ExperimentResponse response = codec.read(json);

        assertThat(response.flagKey()).isEqualTo("flag-b");
        assertThat(response.variants()).hasSize(1);
        assertThat(response.variants().get(0).key()).isEqualTo("variant-a");
        assertThat(response.variants().get(0).value().value()).isEqualTo(123);
        assertThat(response.variants().get(0).value().type()).isEqualTo(FeatureValueType.NUMBER);
        assertThat(response.state()).isEqualTo(ExperimentState.APPROVED);
        assertThat(response.version()).isEqualTo(4L);
    }

    @Test
    void read_shouldThrowExceptionAndFailOnInvalidJson() {
        assertThatThrownBy(() -> codec.read("{")).isInstanceOf(RuntimeException.class);
    }
}
