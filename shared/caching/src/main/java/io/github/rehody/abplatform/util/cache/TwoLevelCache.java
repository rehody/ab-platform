package io.github.rehody.abplatform.util.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Optional;
import java.util.function.Supplier;

public class TwoLevelCache<T> {

    private final CacheStore<T> store;
    private final Cache<String, T> l1ValueCache;
    private final Cache<String, Boolean> l1MissCache;

    private Integer invalidationListenerId;

    public TwoLevelCache(CacheStore<T> store, LocalCacheConfig config) {
        this.store = store;
        this.l1ValueCache = Caffeine.newBuilder()
                .maximumSize(config.valueSize())
                .expireAfterWrite(config.valueTtl())
                .recordStats()
                .build();

        this.l1MissCache = Caffeine.newBuilder()
                .maximumSize(config.missSize())
                .expireAfterWrite(config.missTtl())
                .recordStats()
                .build();
    }

    public void start() {
        invalidationListenerId = store.subscribeInvalidation(this::invalidateLocalCaches);
    }

    public void stop() {
        if (invalidationListenerId != null) {
            store.unsubscribeInvalidation(invalidationListenerId);
        }
    }

    public void invalidate(String key) {
        String normalizedKey = normalizeKey(key);
        invalidateLocalCaches(normalizedKey);
        store.invalidate(normalizedKey);
        store.publishInvalidation(normalizedKey);
    }

    public Optional<T> getOrLoad(String key, Supplier<Optional<T>> loader) {
        String normalizedKey = normalizeKey(key);

        T l1Value = l1ValueCache.getIfPresent(normalizedKey);
        if (l1Value != null) {
            return Optional.of(l1Value);
        }

        if (hasL1MissMarker(normalizedKey)) {
            return Optional.empty();
        }

        T resolvedValue = l1ValueCache.get(normalizedKey, missingKey -> resolveFromL2ThenSource(missingKey, loader));
        return Optional.ofNullable(resolvedValue);
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Cache key cannot be null or blank");
        }
        return key.trim();
    }

    private boolean hasL1MissMarker(String key) {
        return Boolean.TRUE.equals(l1MissCache.getIfPresent(key));
    }

    private T resolveFromL2ThenSource(String key, Supplier<Optional<T>> loader) {
        Optional<T> l2Response = store.readValue(key);
        if (l2Response.isPresent()) {
            T l2Value = l2Response.get();
            l1MissCache.invalidate(key);
            return l2Value;
        }

        if (store.hasMiss(key)) {
            l1MissCache.put(key, Boolean.TRUE);
        }

        if (hasL1MissMarker(key)) {
            return null;
        }

        return resolveFromSourceAndRefresh(key, loader);
    }

    private T resolveFromSourceAndRefresh(String key, Supplier<Optional<T>> loader) {
        Optional<T> sourceResponse = loader.get();
        if (sourceResponse.isPresent()) {
            T sourceValue = sourceResponse.get();
            store.writeValue(key, sourceValue);
            l1MissCache.invalidate(key);
            return sourceValue;
        }

        l1MissCache.put(key, Boolean.TRUE);
        store.writeMiss(key);
        return null;
    }

    private void invalidateLocalCaches(String key) {
        l1ValueCache.invalidate(key);
        l1MissCache.invalidate(key);
    }
}
