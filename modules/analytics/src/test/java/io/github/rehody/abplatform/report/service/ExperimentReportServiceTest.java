package io.github.rehody.abplatform.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.cache.ExperimentMetricReportCache;
import io.github.rehody.abplatform.cache.ExperimentMetricReportCacheKeyFactory;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.service.MetricDefinitionService;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.report.factory.CountableMetricReportAssembler;
import io.github.rehody.abplatform.report.factory.ExperimentReportWindowFactory;
import io.github.rehody.abplatform.report.factory.UniqueMetricReportAssembler;
import io.github.rehody.abplatform.report.model.CountableMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReportMeta;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.model.UniqueMetricReport;
import io.github.rehody.abplatform.report.repository.AssignmentEventReportRepository;
import io.github.rehody.abplatform.report.repository.CountableMetricEventReportRepository;
import io.github.rehody.abplatform.report.repository.UniqueMetricEventReportRepository;
import io.github.rehody.abplatform.report.repository.aggregate.AssignmentVariantAggregate;
import io.github.rehody.abplatform.report.repository.aggregate.CountableMetricVariantAggregate;
import io.github.rehody.abplatform.report.repository.aggregate.UniqueMetricVariantAggregate;
import io.github.rehody.abplatform.service.ExperimentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentReportServiceTest {

    @Mock
    private ExperimentMetricReportCache experimentMetricReportCache;

    @Mock
    private ExperimentService experimentService;

    @Mock
    private MetricDefinitionService metricDefinitionService;

    @Mock
    private AssignmentEventReportRepository assignmentEventReportRepository;

    @Mock
    private UniqueMetricEventReportRepository uniqueMetricEventReportRepository;

    @Mock
    private CountableMetricEventReportRepository countableMetricEventReportRepository;

    @Mock
    private ExperimentReportWindowFactory experimentReportWindowFactory;

    @Mock
    private UniqueMetricReportAssembler uniqueMetricReportAssembler;

    @Mock
    private CountableMetricReportAssembler countableMetricReportAssembler;

    private ExperimentReportService experimentReportService;
    private ExperimentMetricReportCacheKeyFactory experimentMetricReportCacheKeyFactory;

    @BeforeEach
    void setUp() {
        experimentMetricReportCacheKeyFactory = new ExperimentMetricReportCacheKeyFactory();
        experimentReportService = new ExperimentReportService(
                experimentMetricReportCache,
                experimentMetricReportCacheKeyFactory,
                experimentService,
                metricDefinitionService,
                assignmentEventReportRepository,
                uniqueMetricEventReportRepository,
                countableMetricEventReportRepository,
                experimentReportWindowFactory,
                uniqueMetricReportAssembler,
                countableMetricReportAssembler);
    }

    @Test
    void getExperimentReport_shouldReuseCachedReport() {
        Experiment experiment = experiment();
        MetricDefinition metricDefinition = countableMetricDefinition();
        CountableMetricReport cachedReport = countableReport(experiment.id());
        String cacheKey = experimentMetricReportCacheKeyFactory.forExperimentMetric(experiment.id(), "orders");

        when(experimentMetricReportCache.getOrLoad(eq(cacheKey), any())).thenReturn(Optional.of(cachedReport));

        ExperimentMetricReport response = experimentReportService.getExperimentReport(experiment.id(), "orders");

        assertThat(response).isEqualTo(cachedReport);
    }

    @Test
    void getExperimentReport_shouldBuildCountableReportOnCacheMiss() {
        Experiment experiment = experiment();
        MetricDefinition metricDefinition = countableMetricDefinition();
        ExperimentReportWindow reportWindow = reportWindow();
        CountableMetricReport assembledReport = countableReport(experiment.id());
        String cacheKey = experimentMetricReportCacheKeyFactory.forExperimentMetric(experiment.id(), "orders");
        AtomicReference<ExperimentMetricReport> cachedValue = new AtomicReference<>();

        when(experimentMetricReportCache.getOrLoad(eq(cacheKey), any())).thenAnswer(invocation -> {
            Supplier<Optional<ExperimentMetricReport>> loader = invocation.getArgument(1);
            if (cachedValue.get() != null) {
                return Optional.of(cachedValue.get());
            }
            Optional<ExperimentMetricReport> loaded = loader.get();
            loaded.ifPresent(cachedValue::set);
            return loaded;
        });
        when(experimentService.getById(experiment.id())).thenReturn(experiment);
        when(metricDefinitionService.getByKey("orders")).thenReturn(metricDefinition);
        when(experimentReportWindowFactory.create(eq(experiment), any())).thenReturn(reportWindow);
        when(assignmentEventReportRepository.findParticipantCountsByVariant(experiment.id(), reportWindow))
                .thenReturn(List.of(
                        new AssignmentVariantAggregate(
                                experiment.variants().get(0).id(), 11),
                        new AssignmentVariantAggregate(
                                experiment.variants().get(1).id(), 13)));
        when(countableMetricEventReportRepository.findMetricStatsByVariant(experiment.id(), "orders", reportWindow))
                .thenReturn(List.of(
                        new CountableMetricVariantAggregate(
                                experiment.variants().get(0).id(), 3, 4),
                        new CountableMetricVariantAggregate(
                                experiment.variants().get(1).id(), 5, 8)));
        when(countableMetricReportAssembler.assemble(
                        eq(experiment), eq(metricDefinition), any(), any(), any(), eq(reportWindow)))
                .thenReturn(assembledReport);

        ExperimentMetricReport first = experimentReportService.getExperimentReport(experiment.id(), "orders");
        ExperimentMetricReport second = experimentReportService.getExperimentReport(experiment.id(), "orders");

        assertThat(first).isEqualTo(assembledReport);
        assertThat(second).isEqualTo(assembledReport);
    }

    @Test
    void getExperimentReport_shouldBuildUniqueReportOnCacheMiss() {
        Experiment experiment = experiment();
        MetricDefinition metricDefinition = uniqueMetricDefinition();
        ExperimentReportWindow reportWindow = reportWindow();
        UniqueMetricReport assembledReport = uniqueReport(experiment.id());
        String cacheKey = experimentMetricReportCacheKeyFactory.forExperimentMetric(experiment.id(), "signup");

        when(experimentMetricReportCache.getOrLoad(eq(cacheKey), any()))
                .thenAnswer(
                        invocation -> ((Supplier<Optional<ExperimentMetricReport>>) invocation.getArgument(1)).get());
        when(experimentService.getById(experiment.id())).thenReturn(experiment);
        when(metricDefinitionService.getByKey("signup")).thenReturn(metricDefinition);
        when(experimentReportWindowFactory.create(eq(experiment), any())).thenReturn(reportWindow);
        when(assignmentEventReportRepository.findParticipantCountsByVariant(experiment.id(), reportWindow))
                .thenReturn(List.of(
                        new AssignmentVariantAggregate(
                                experiment.variants().get(0).id(), 11),
                        new AssignmentVariantAggregate(
                                experiment.variants().get(1).id(), 13)));
        when(uniqueMetricEventReportRepository.findParticipantCountsByVariant(experiment.id(), "signup", reportWindow))
                .thenReturn(List.of(
                        new UniqueMetricVariantAggregate(
                                experiment.variants().get(0).id(), 3),
                        new UniqueMetricVariantAggregate(
                                experiment.variants().get(1).id(), 5)));
        when(uniqueMetricReportAssembler.assemble(
                        eq(experiment), eq(metricDefinition), any(), any(), any(), eq(reportWindow)))
                .thenReturn(assembledReport);

        ExperimentMetricReport response = experimentReportService.getExperimentReport(experiment.id(), "signup");

        assertThat(response).isEqualTo(assembledReport);
    }

    @Test
    void getExperimentReport_shouldRejectEmptyCacheLoaderResponse() {
        when(experimentMetricReportCache.getOrLoad(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentReportService.getExperimentReport(UUID.randomUUID(), "orders"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Experiment metric report cache loader returned empty");
    }

    private Experiment experiment() {
        UUID experimentId = UUID.randomUUID();
        return new Experiment(
                experimentId,
                "flag-orders",
                "CHECKOUT",
                List.of(
                        new ExperimentVariant(
                                UUID.randomUUID(),
                                "treatment",
                                new FeatureValue(false, FeatureValueType.BOOL),
                                1,
                                new BigDecimal("0.50"),
                                ExperimentVariantType.REGULAR),
                        new ExperimentVariant(
                                UUID.randomUUID(),
                                "control",
                                new FeatureValue(true, FeatureValueType.BOOL),
                                0,
                                new BigDecimal("0.50"),
                                ExperimentVariantType.CONTROL)),
                ExperimentState.RUNNING,
                3L,
                Instant.parse("2026-04-05T09:00:00Z"),
                null);
    }

    private MetricDefinition countableMetricDefinition() {
        return new MetricDefinition(
                UUID.randomUUID(),
                "orders",
                "Orders",
                MetricType.COUNTABLE,
                MetricDirection.MORE_IS_BETTER,
                MetricSeverity.HIGH,
                new BigDecimal("0.10"));
    }

    private MetricDefinition uniqueMetricDefinition() {
        return new MetricDefinition(
                UUID.randomUUID(),
                "signup",
                "Signup",
                MetricType.UNIQUE,
                MetricDirection.MORE_IS_BETTER,
                MetricSeverity.MEDIUM,
                new BigDecimal("0.15"));
    }

    private ExperimentReportWindow reportWindow() {
        return new ExperimentReportWindow(Instant.parse("2026-04-05T09:00:00Z"), Instant.parse("2026-04-05T10:00:00Z"));
    }

    private CountableMetricReport countableReport(UUID experimentId) {
        return new CountableMetricReport(
                reportMeta(experimentId, MetricType.COUNTABLE, "orders"),
                24,
                8,
                12,
                new BigDecimal("0.3333"),
                new BigDecimal("0.5000"),
                List.of(new CountableMetricReport.CountableVariantSummary(
                        UUID.randomUUID(),
                        "control",
                        0,
                        12,
                        4,
                        6,
                        new BigDecimal("0.3333"),
                        new BigDecimal("0.5000"))));
    }

    private UniqueMetricReport uniqueReport(UUID experimentId) {
        return new UniqueMetricReport(
                reportMeta(experimentId, MetricType.UNIQUE, "signup"),
                24,
                8,
                new BigDecimal("0.3333"),
                List.of(new UniqueMetricReport.UniqueVariantSummary(
                        UUID.randomUUID(), "control", 0, 12, 4, new BigDecimal("0.3333"))));
    }

    private ExperimentMetricReportMeta reportMeta(UUID experimentId, MetricType metricType, String metricKey) {
        return new ExperimentMetricReportMeta(
                experimentId,
                "flag-orders",
                metricKey,
                metricType,
                ExperimentState.RUNNING,
                Instant.parse("2026-04-05T09:00:00Z"),
                null,
                Instant.parse("2026-04-05T09:00:00Z"),
                Instant.parse("2026-04-05T10:00:00Z"));
    }
}
