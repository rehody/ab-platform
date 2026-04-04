package io.github.rehody.abplatform.binding.service;

import static org.mockito.Mockito.verify;

import io.github.rehody.abplatform.cache.ExperimentMetricReportCache;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentMetricBindingCacheInvalidatorTest {

    @Mock
    private ExperimentMetricReportCache experimentMetricReportCache;

    @Test
    void invalidateReports_shouldInvalidateEachReportCacheKey() {
        UUID experimentId = UUID.randomUUID();
        ExperimentMetricBindingCacheInvalidator invalidator =
                new ExperimentMetricBindingCacheInvalidator(experimentMetricReportCache);

        invalidator.invalidateReports(experimentId, List.of("orders", "revenue"));

        verify(experimentMetricReportCache).invalidate("%s:metric:%s".formatted(experimentId, "orders"));
        verify(experimentMetricReportCache).invalidate("%s:metric:%s".formatted(experimentId, "revenue"));
    }
}
