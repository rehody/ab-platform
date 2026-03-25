package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.util.cache.CacheCodec;
import tools.jackson.databind.ObjectMapper;

record FeatureFlagResponseCacheCodec(ObjectMapper objectMapper) implements CacheCodec<FeatureFlagResponse> {

    @Override
    public String write(FeatureFlagResponse value) {
        return objectMapper.writeValueAsString(value);
    }

    @Override
    public FeatureFlagResponse read(String value) {
        return objectMapper.readValue(value, FeatureFlagResponse.class);
    }
}
