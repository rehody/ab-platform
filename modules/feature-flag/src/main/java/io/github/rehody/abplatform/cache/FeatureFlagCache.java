package io.github.rehody.abplatform.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.function.Supplier;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagCache {

    private final RedissonClient redissonClient;
    private final FeatureFlagRedisStore redisStore;
    private final Cache<String, FeatureFlagResponse> l1ValueCache;
    private final Cache<String, Boolean> l1MissCache;
    private final String invalidationTopicName;

    private RTopic invalidationTopic;
    private Integer invalidationListenerId;

    public FeatureFlagCache(
            RedissonClient redissonClient, FeatureFlagRedisStore redisStore, FeatureFlagCacheProperties properties) {
        this.redissonClient = redissonClient;
        this.redisStore = redisStore;
        this.invalidationTopicName = properties.getInvalidationTopic();

        this.l1ValueCache = Caffeine.newBuilder()
                .maximumSize(properties.getL1ValueSize())
                .expireAfterWrite(properties.getL1ValueTtl())
                .recordStats()
                .build();

        this.l1MissCache = Caffeine.newBuilder()
                .maximumSize(properties.getL1MissSize())
                .expireAfterWrite(properties.getL1MissTtl())
                .recordStats()
                .build();
    }

    @PostConstruct
    public void subscribeInvalidationTopic() {
        invalidationTopic = redissonClient.getTopic(invalidationTopicName);
        invalidationListenerId = invalidationTopic.addListener(String.class, (_, key) -> invalidateLocalCaches(key));
    }

    @PreDestroy
    public void unsubscribeInvalidationTopic() {
        if (invalidationTopic != null && invalidationListenerId != null) {
            invalidationTopic.removeListener(invalidationListenerId);
        }
    }

    public void invalidate(String key) {
        String normalizedKey = normalizeKey(key);
        invalidateLocalCaches(normalizedKey);
        redisStore.invalidate(normalizedKey);
        redisStore.publishInvalidation(normalizedKey);
    }

    public Optional<FeatureFlagResponse> getOrLoad(String key, Supplier<Optional<FeatureFlagResponse>> loader) {
        String normalizedKey = normalizeKey(key);

        Optional<FeatureFlagResponse> l1Response = readFromL1(normalizedKey);
        if (l1Response.isPresent()) {
            return l1Response;
        }

        if (hasL1MissMarker(normalizedKey)) {
            return Optional.empty();
        }

        FeatureFlagResponse resolvedValue =
                l1ValueCache.get(normalizedKey, missingKey -> readRedisThenSource(missingKey, loader));

        return Optional.ofNullable(resolvedValue);
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("FeatureFlagCache key cannot be null or blank");
        }
        return key.trim();
    }

    private Optional<FeatureFlagResponse> readFromL1(String key) {
        return Optional.ofNullable(l1ValueCache.getIfPresent(key));
    }

    private boolean hasL1MissMarker(String key) {
        return Boolean.TRUE.equals(l1MissCache.getIfPresent(key));
    }

    private FeatureFlagResponse readRedisThenSource(String key, Supplier<Optional<FeatureFlagResponse>> loader) {
        Optional<FeatureFlagResponse> redisResponse = readFromRedis(key);
        if (redisResponse.isPresent()) {
            return redisResponse.get();
        }
        if (hasL1MissMarker(key)) {
            return null;
        }

        return loadFromSourceAndRefreshCaches(key, loader);
    }

    private Optional<FeatureFlagResponse> readFromRedis(String key) {
        Optional<FeatureFlagResponse> redisResponse = redisStore.readValue(key);

        if (redisResponse.isPresent()) {
            l1MissCache.invalidate(key);
            return redisResponse;
        }

        if (redisStore.hasMiss(key)) {
            l1MissCache.put(key, Boolean.TRUE);
        }

        return Optional.empty();
    }

    private FeatureFlagResponse loadFromSourceAndRefreshCaches(
            String key, Supplier<Optional<FeatureFlagResponse>> loader) {

        Optional<FeatureFlagResponse> sourceResponse = loader.get();
        if (sourceResponse.isPresent()) {
            FeatureFlagResponse sourceValue = sourceResponse.get();
            redisStore.writeValue(key, sourceValue);
            l1MissCache.invalidate(key);
            return sourceValue;
        }

        l1MissCache.put(key, Boolean.TRUE);
        redisStore.writeMiss(key);
        return null;
    }

    private void invalidateLocalCaches(String key) {
        l1ValueCache.invalidate(key);
        l1MissCache.invalidate(key);
    }
}
