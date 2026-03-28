package io.github.rehody.abplatform.util.cache;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ObjectMapperCacheCodec<T> implements CacheCodec<T> {

    private final ObjectMapper objectMapper;
    private final JavaType targetType;

    public static <T> ObjectMapperCacheCodec<T> forClass(ObjectMapper objectMapper, Class<T> targetClass) {
        JavaType targetType = objectMapper.getTypeFactory().constructType(targetClass);
        return new ObjectMapperCacheCodec<>(objectMapper, targetType);
    }

    @Override
    public String write(T value) {
        return objectMapper.writeValueAsString(value);
    }

    @Override
    public T read(String value) {
        return objectMapper.readValue(value, targetType);
    }
}
