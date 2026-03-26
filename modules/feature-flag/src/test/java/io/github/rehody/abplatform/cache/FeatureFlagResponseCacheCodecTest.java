package io.github.rehody.abplatform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FeatureFlagResponseCacheCodecTest {

    private final FeatureFlagResponseCacheCodec codec = new FeatureFlagResponseCacheCodec(new ObjectMapper());

    @Test
    void write_shouldSerializeResponseAndPreserveFields() {
        FeatureFlagResponse response =
                new FeatureFlagResponse("flag-a", new FeatureValue(true, FeatureValueType.BOOL), 6L);

        String json = codec.write(response);

        assertThat(json).contains("\"key\":\"flag-a\"");
        assertThat(json).contains("\"type\":\"BOOL\"");
        assertThat(json).contains("\"version\":6");
    }

    @Test
    void read_shouldDeserializeResponseAndRestoreFields() {
        String json = """
                {"key":"flag-b","defaultValue":{"value":123,"type":"NUMBER"},"version":4}
                """;

        FeatureFlagResponse response = codec.read(json);

        assertThat(response.key()).isEqualTo("flag-b");
        assertThat(response.defaultValue().value()).isEqualTo(123);
        assertThat(response.defaultValue().type()).isEqualTo(FeatureValueType.NUMBER);
        assertThat(response.version()).isEqualTo(4L);
    }

    @Test
    void read_shouldThrowExceptionAndFailOnInvalidJson() {
        assertThatThrownBy(() -> codec.read("{")).isInstanceOf(RuntimeException.class);
    }
}
