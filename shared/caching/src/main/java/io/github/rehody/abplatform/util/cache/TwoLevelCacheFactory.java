package io.github.rehody.abplatform.util.cache;

import org.redisson.api.RedissonClient;
import tools.jackson.databind.ObjectMapper;

public final class TwoLevelCacheFactory {

    private TwoLevelCacheFactory() {}

    public static <T> TwoLevelCache<T> create(
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            TwoLevelCacheProperties properties,
            Class<T> cachedType) {

        LocalCacheConfig localConfig = new LocalCacheConfig(
                properties.getL1ValueTtl(),
                properties.getL1MissTtl(),
                properties.getL1ValueSize(),
                properties.getL1MissSize());

        RedisCacheConfig redisConfig = new RedisCacheConfig(
                properties.getL2ValueTtl(),
                properties.getL2MissTtl(),
                properties.getTtlSpread(),
                properties.getRedisKeyPrefix(),
                properties.getInvalidationTopic());

        CacheCodec<T> codec = ObjectMapperCacheCodec.forClass(objectMapper, cachedType);

        return new TwoLevelCache<>(new RedisCacheStore<>(redissonClient, codec, redisConfig), localConfig);
    }
}
