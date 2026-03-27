package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.dto.response.ExperimentResponse;
import io.github.rehody.abplatform.util.cache.CacheCodec;
import tools.jackson.databind.ObjectMapper;

record ExperimentResponseCacheCodec(ObjectMapper objectMapper) implements CacheCodec<ExperimentResponse> {

    @Override
    public String write(ExperimentResponse value) {
        return objectMapper.writeValueAsString(value);
    }

    @Override
    public ExperimentResponse read(String value) {
        return objectMapper.readValue(value, ExperimentResponse.class);
    }
}
