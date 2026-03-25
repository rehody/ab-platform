package io.github.rehody.abplatform.util.cache;

public interface CacheCodec<T> {

    String write(T value) throws Exception;

    T read(String value) throws Exception;
}
