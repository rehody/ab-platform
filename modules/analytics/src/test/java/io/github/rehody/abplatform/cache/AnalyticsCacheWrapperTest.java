package io.github.rehody.abplatform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.report.model.CountableMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReportMeta;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AnalyticsCacheWrapperTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> valueBucket;

    @Mock
    private RBucket<String> missBucket;

    @Mock
    private RTopic topic;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        when(redissonClient.getBucket(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (key.contains(":value:")) {
                return valueBucket;
            }

            return missBucket;
        });
        when(topic.addListener(org.mockito.ArgumentMatchers.eq(String.class), any()))
                .thenReturn(1);
    }

    @Test
    void metricDefinitionCache_shouldLoadCacheInvalidateAndUnsubscribe() {
        MetricDefinitionCache cache =
                new MetricDefinitionCache(redissonClient, objectMapper, metricDefinitionCacheProperties());
        MetricDefinition metricDefinition = new MetricDefinition(
                UUID.randomUUID(),
                "orders",
                "Orders",
                MetricType.COUNTABLE,
                MetricDirection.MORE_IS_BETTER,
                MetricSeverity.HIGH,
                new BigDecimal("0.10"));
        AtomicInteger loaderCalls = new AtomicInteger();
        when(valueBucket.get()).thenReturn(null);
        when(missBucket.get()).thenReturn(null);

        cache.subscribeInvalidationTopic();

        Optional<MetricDefinition> first = cache.getOrLoad(" orders ", () -> {
            loaderCalls.incrementAndGet();
            return Optional.of(metricDefinition);
        });
        Optional<MetricDefinition> second = cache.getOrLoad("orders", Optional::empty);

        cache.invalidate(" orders ");
        cache.unsubscribeInvalidationTopic();

        assertThat(first).contains(metricDefinition);
        assertThat(second).contains(metricDefinition);
        assertThat(loaderCalls.get()).isEqualTo(1);
        verify(topic).publish("orders");
        verify(topic).removeListener(1);
    }

    @Test
    void experimentMetricReportCache_shouldLoadCacheInvalidateAndUnsubscribe() {
        ExperimentMetricReportCache cache =
                new ExperimentMetricReportCache(redissonClient, objectMapper, experimentMetricReportCacheProperties());
        CountableMetricReport report = new CountableMetricReport(
                new ExperimentMetricReportMeta(
                        UUID.randomUUID(),
                        "flag-orders",
                        "orders",
                        MetricType.COUNTABLE,
                        ExperimentState.RUNNING,
                        null,
                        null,
                        null,
                        null),
                20,
                8,
                12,
                new BigDecimal("0.4000"),
                new BigDecimal("0.6000"),
                List.of(new CountableMetricReport.CountableVariantSummary(
                        UUID.randomUUID(),
                        "control",
                        0,
                        10,
                        4,
                        6,
                        new BigDecimal("0.4000"),
                        new BigDecimal("0.6000"))));
        AtomicInteger loaderCalls = new AtomicInteger();
        when(valueBucket.get()).thenReturn(null);
        when(missBucket.get()).thenReturn(null);

        cache.subscribeInvalidationTopic();

        Optional<ExperimentMetricReport> first = cache.getOrLoad(" report-key ", () -> {
            loaderCalls.incrementAndGet();
            return Optional.of(report);
        });
        Optional<ExperimentMetricReport> second = cache.getOrLoad("report-key", Optional::empty);

        cache.invalidate(" report-key ");
        cache.unsubscribeInvalidationTopic();

        assertThat(first).contains(report);
        assertThat(second).contains(report);
        assertThat(loaderCalls.get()).isEqualTo(1);
        verify(topic).publish("report-key");
        verify(topic).removeListener(1);
    }

    private MetricDefinitionCacheProperties metricDefinitionCacheProperties() {
        MetricDefinitionCacheProperties properties = new MetricDefinitionCacheProperties();
        properties.setL1ValueTtl(Duration.ofMinutes(1));
        properties.setL1MissTtl(Duration.ofSeconds(30));
        properties.setL1ValueSize(10);
        properties.setL1MissSize(10);
        properties.setL2ValueTtl(Duration.ofMinutes(5));
        properties.setL2MissTtl(Duration.ofMinutes(1));
        properties.setTtlSpread(0.0d);
        properties.setRedisKeyPrefix("metric");
        properties.setInvalidationTopic("metric-topic");
        return properties;
    }

    private ExperimentMetricReportCacheProperties experimentMetricReportCacheProperties() {
        ExperimentMetricReportCacheProperties properties = new ExperimentMetricReportCacheProperties();
        properties.setL1ValueTtl(Duration.ofMinutes(1));
        properties.setL1MissTtl(Duration.ofSeconds(30));
        properties.setL1ValueSize(10);
        properties.setL1MissSize(10);
        properties.setL2ValueTtl(Duration.ofMinutes(5));
        properties.setL2MissTtl(Duration.ofMinutes(1));
        properties.setTtlSpread(0.0d);
        properties.setRedisKeyPrefix("report");
        properties.setInvalidationTopic("report-topic");
        return properties;
    }
}
