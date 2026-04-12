package io.github.rehody.abplatform.util.cache;

public interface CacheCodec<T> {

    String write(T value);

    T read(String value);
}
