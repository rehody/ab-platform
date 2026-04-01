package io.github.rehody.abplatform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.util.cache.ObjectMapperCacheCodec;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FeatureFlagCacheCodecTest {

    private final ObjectMapperCacheCodec<CachedFeatureFlag> codec =
            ObjectMapperCacheCodec.forClass(new ObjectMapper(), CachedFeatureFlag.class);

    @Test
    void write_shouldSerializeResponseAndPreserveFields() {
        CachedFeatureFlag cachedFeatureFlag = new CachedFeatureFlag(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "flag-a",
                new FeatureValue(true, FeatureValueType.BOOL),
                6L);

        String json = codec.write(cachedFeatureFlag);

        assertThat(json).contains("\"id\":\"11111111-1111-1111-1111-111111111111\"");
        assertThat(json).contains("\"key\":\"flag-a\"");
        assertThat(json).contains("\"type\":\"BOOL\"");
        assertThat(json).contains("\"version\":6");
    }

    @Test
    void read_shouldDeserializeResponseAndRestoreFields() {
        String json = """
                {"id":"11111111-1111-1111-1111-111111111111","key":"flag-b","defaultValue":{"value":123,"type":"NUMBER"},"version":4}
                """;

        CachedFeatureFlag cachedFeatureFlag = codec.read(json);

        assertThat(cachedFeatureFlag.id()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(cachedFeatureFlag.key()).isEqualTo("flag-b");
        assertThat(cachedFeatureFlag.defaultValue().value()).isEqualTo(123);
        assertThat(cachedFeatureFlag.defaultValue().type()).isEqualTo(FeatureValueType.NUMBER);
        assertThat(cachedFeatureFlag.version()).isEqualTo(4L);
    }

    @Test
    void read_shouldThrowExceptionAndFailOnInvalidJson() {
        assertThatThrownBy(() -> codec.read("{")).isInstanceOf(RuntimeException.class);
    }
}
