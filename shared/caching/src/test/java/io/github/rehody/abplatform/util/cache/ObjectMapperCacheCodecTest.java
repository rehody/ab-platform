package io.github.rehody.abplatform.util.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ObjectMapperCacheCodecTest {

    @Test
    void forClass_shouldWriteAndReadValueUsingObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectMapperCacheCodec<TestValue> codec = ObjectMapperCacheCodec.forClass(objectMapper, TestValue.class);
        TestValue value = new TestValue("flag-a", 42);

        String serialized = codec.write(value);
        TestValue deserialized = codec.read(serialized);

        assertThat(serialized).contains("flag-a");
        assertThat(deserialized).isEqualTo(value);
    }

    private record TestValue(String key, int count) {}
}
