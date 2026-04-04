package io.github.rehody.abplatform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.util.cache.ObjectMapperCacheCodec;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ExperimentCacheCodecTest {

    private final ObjectMapperCacheCodec<CachedExperiment> codec =
            ObjectMapperCacheCodec.forClass(new ObjectMapper(), CachedExperiment.class);

    @Test
    void write_shouldSerializeResponseAndPreserveFields() {
        CachedExperiment cachedExperiment = new CachedExperiment(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "flag-a",
                "CHECKOUT",
                List.of(new ExperimentVariant(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE,
                        ExperimentVariantType.CONTROL)),
                ExperimentState.RUNNING,
                6L,
                Instant.parse("2026-03-30T10:15:30Z"),
                null);

        String json = codec.write(cachedExperiment);

        assertThat(json).contains("\"id\":\"00000000-0000-0000-0000-000000000001\"");
        assertThat(json).contains("\"flagKey\":\"flag-a\"");
        assertThat(json).contains("\"domain\":\"CHECKOUT\"");
        assertThat(json).contains("\"key\":\"control\"");
        assertThat(json).contains("\"type\":\"BOOL\"");
        assertThat(json).contains("\"version\":6");
    }

    @Test
    void read_shouldDeserializeResponseAndRestoreFields() {
        String json = """
                {"id":"00000000-0000-0000-0000-000000000001","flagKey":"flag-b","domain":"CHECKOUT","variants":[{"id":"11111111-1111-1111-1111-111111111111","key":"variant-a","value":{"value":123,"type":"NUMBER"},"position":1,"weight":2,"type":"REGULAR"}],"state":"APPROVED","version":4,"startedAt":null,"completedAt":null}
                """;

        CachedExperiment cachedExperiment = codec.read(json);

        assertThat(cachedExperiment.id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(cachedExperiment.flagKey()).isEqualTo("flag-b");
        assertThat(cachedExperiment.domain()).isEqualTo("CHECKOUT");
        assertThat(cachedExperiment.variants()).hasSize(1);
        assertThat(cachedExperiment.variants().getFirst().key()).isEqualTo("variant-a");
        assertThat(cachedExperiment.variants().getFirst().value().value()).isEqualTo(123);
        assertThat(cachedExperiment.variants().getFirst().value().type()).isEqualTo(FeatureValueType.NUMBER);
        assertThat(cachedExperiment.variants().getFirst().weight()).isEqualByComparingTo("2");
        assertThat(cachedExperiment.variants().getFirst().type()).isEqualTo(ExperimentVariantType.REGULAR);
        assertThat(cachedExperiment.state()).isEqualTo(ExperimentState.APPROVED);
        assertThat(cachedExperiment.version()).isEqualTo(4L);
    }

    @Test
    void read_shouldThrowExceptionAndFailOnInvalidJson() {
        assertThatThrownBy(() -> codec.read("{")).isInstanceOf(RuntimeException.class);
    }
}
