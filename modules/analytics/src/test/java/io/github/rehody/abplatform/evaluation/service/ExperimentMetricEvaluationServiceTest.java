package io.github.rehody.abplatform.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.cache.ExperimentMetricReportCache;
import io.github.rehody.abplatform.cache.ExperimentMetricReportCacheKeyFactory;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.evaluation.builder.ExperimentMetricEvaluationAssembler;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.policy.ExperimentMetricEvaluationPolicy;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.report.factory.CountableMetricReportAssembler;
import io.github.rehody.abplatform.report.factory.ExperimentReportWindowFactory;
import io.github.rehody.abplatform.report.model.CountableMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReportMeta;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.repository.AssignmentEventReportRepository;
import io.github.rehody.abplatform.report.repository.CountableMetricEventReportRepository;
import io.github.rehody.abplatform.report.repository.aggregate.AssignmentVariantAggregate;
import io.github.rehody.abplatform.report.repository.aggregate.CountableMetricVariantAggregate;
import io.github.rehody.abplatform.risk.service.ExperimentMetricRiskService;
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
class ExperimentMetricEvaluationServiceTest {

    @Mock
    private ExperimentMetricReportCache experimentMetricReportCache;

    @Mock
    private ExperimentService experimentService;

    @Mock
    private ExperimentMetricEvaluationPolicy experimentMetricEvaluationPolicy;

    @Mock
    private AssignmentEventReportRepository assignmentEventReportRepository;

    @Mock
    private CountableMetricEventReportRepository countableMetricEventReportRepository;

    @Mock
    private ExperimentReportWindowFactory experimentReportWindowFactory;

    @Mock
    private CountableMetricReportAssembler countableMetricReportAssembler;

    @Mock
    private ExperimentMetricEvaluationAssembler experimentMetricEvaluationAssembler;

    @Mock
    private ExperimentMetricRiskService experimentMetricRiskService;

    private ExperimentMetricEvaluationService experimentMetricEvaluationService;

    private ExperimentMetricReportCacheKeyFactory experimentMetricReportCacheKeyFactory;

    @BeforeEach
    void setUp() {
        experimentMetricReportCacheKeyFactory = new ExperimentMetricReportCacheKeyFactory();
        experimentMetricEvaluationService = new ExperimentMetricEvaluationService(
                experimentMetricReportCache,
                experimentMetricReportCacheKeyFactory,
                experimentService,
                experimentMetricEvaluationPolicy,
                assignmentEventReportRepository,
                countableMetricEventReportRepository,
                experimentReportWindowFactory,
                countableMetricReportAssembler,
                experimentMetricEvaluationAssembler,
                experimentMetricRiskService);
    }

    @Test
    void shouldUseSharedReportCacheKeyWhenGettingEvaluationReport() {
        Experiment experiment = experiment();
        MetricDefinition metricDefinition = metricDefinition();
        CountableMetricReport countableMetricReport = countableMetricReport(experiment, metricDefinition);
        ExperimentMetricEvaluationReport evaluationReport = evaluationReport();
        String cacheKey =
                experimentMetricReportCacheKeyFactory.forExperimentMetric(experiment.id(), metricDefinition.key());

        when(experimentService.getById(experiment.id())).thenReturn(experiment);
        when(experimentMetricEvaluationPolicy.getMetricDefinitionForEvaluation(experiment.id(), metricDefinition.key()))
                .thenReturn(metricDefinition);
        when(experimentMetricReportCache.getOrLoad(eq(cacheKey), any())).thenReturn(Optional.of(countableMetricReport));
        when(experimentMetricRiskService.getRisks(experiment.id(), metricDefinition.key()))
                .thenReturn(List.of());
        when(experimentMetricEvaluationAssembler.assemble(
                        eq(experiment), eq(metricDefinition), any(), any(), any(), any(), any()))
                .thenReturn(evaluationReport);

        ExperimentMetricEvaluationReport response =
                experimentMetricEvaluationService.getEvaluationReport(experiment.id(), metricDefinition.key());

        assertThat(response).isEqualTo(evaluationReport);
        verify(experimentMetricReportCache).getOrLoad(eq(cacheKey), any());
    }

    @Test
    void shouldReuseCachedCountableReportWhenCacheHits() {
        Experiment experiment = experiment();
        MetricDefinition metricDefinition = metricDefinition();
        CountableMetricReport countableMetricReport = countableMetricReport(experiment, metricDefinition);
        ExperimentMetricEvaluationReport evaluationReport = evaluationReport();
        String cacheKey =
                experimentMetricReportCacheKeyFactory.forExperimentMetric(experiment.id(), metricDefinition.key());

        when(experimentService.getById(experiment.id())).thenReturn(experiment);
        when(experimentMetricEvaluationPolicy.getMetricDefinitionForEvaluation(experiment.id(), metricDefinition.key()))
                .thenReturn(metricDefinition);
        when(experimentMetricReportCache.getOrLoad(eq(cacheKey), any())).thenReturn(Optional.of(countableMetricReport));
        when(experimentMetricRiskService.getRisks(experiment.id(), metricDefinition.key()))
                .thenReturn(List.of());
        when(experimentMetricEvaluationAssembler.assemble(
                        eq(experiment), eq(metricDefinition), any(), any(), any(), any(), any()))
                .thenReturn(evaluationReport);

        ExperimentMetricEvaluationReport response =
                experimentMetricEvaluationService.getEvaluationReport(experiment.id(), metricDefinition.key());

        assertThat(response).isEqualTo(evaluationReport);
        verify(assignmentEventReportRepository, never()).findParticipantCountsByVariant(any(), any());
        verify(countableMetricEventReportRepository, never()).findMetricStatsByVariant(any(), any(), any());
        verify(countableMetricReportAssembler, never()).assemble(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldLoadCountableReportOnceAndReuseCacheWrapperOnSubsequentReads() {
        Experiment experiment = experiment();
        MetricDefinition metricDefinition = metricDefinition();
        CountableMetricReport countableMetricReport = countableMetricReport(experiment, metricDefinition);
        ExperimentMetricEvaluationReport evaluationReport = evaluationReport();
        ExperimentReportWindow reportWindow = new ExperimentReportWindow(
                Instant.parse("2026-04-04T10:00:00Z"), Instant.parse("2026-04-04T11:00:00Z"));
        String cacheKey =
                experimentMetricReportCacheKeyFactory.forExperimentMetric(experiment.id(), metricDefinition.key());
        AtomicReference<ExperimentMetricReport> cachedReport = new AtomicReference<>();

        when(experimentService.getById(experiment.id())).thenReturn(experiment);
        when(experimentMetricEvaluationPolicy.getMetricDefinitionForEvaluation(experiment.id(), metricDefinition.key()))
                .thenReturn(metricDefinition);
        when(experimentMetricReportCache.getOrLoad(eq(cacheKey), any())).thenAnswer(invocation -> {
            Supplier<Optional<ExperimentMetricReport>> loader = invocation.getArgument(1);
            if (cachedReport.get() != null) {
                return Optional.of(cachedReport.get());
            }

            Optional<ExperimentMetricReport> loadedReport = loader.get();
            loadedReport.ifPresent(cachedReport::set);
            return loadedReport;
        });
        when(experimentReportWindowFactory.create(eq(experiment), any())).thenReturn(reportWindow);
        when(assignmentEventReportRepository.findParticipantCountsByVariant(experiment.id(), reportWindow))
                .thenReturn(List.of(
                        new AssignmentVariantAggregate(
                                experiment.variants().get(0).id(), 100),
                        new AssignmentVariantAggregate(
                                experiment.variants().get(1).id(), 120)));
        when(countableMetricEventReportRepository.findMetricStatsByVariant(
                        experiment.id(), metricDefinition.key(), reportWindow))
                .thenReturn(List.of(
                        new CountableMetricVariantAggregate(
                                experiment.variants().get(0).id(), 30, 40),
                        new CountableMetricVariantAggregate(
                                experiment.variants().get(1).id(), 35, 48)));
        when(countableMetricReportAssembler.assemble(
                        eq(experiment), eq(metricDefinition), any(), any(), any(), eq(reportWindow)))
                .thenReturn(countableMetricReport);
        when(experimentMetricRiskService.getRisks(experiment.id(), metricDefinition.key()))
                .thenReturn(List.of());
        when(experimentMetricEvaluationAssembler.assemble(
                        eq(experiment), eq(metricDefinition), any(), any(), any(), any(), any()))
                .thenReturn(evaluationReport);

        ExperimentMetricEvaluationReport first =
                experimentMetricEvaluationService.getEvaluationReport(experiment.id(), metricDefinition.key());
        ExperimentMetricEvaluationReport second =
                experimentMetricEvaluationService.getEvaluationReport(experiment.id(), metricDefinition.key());

        assertThat(first).isEqualTo(evaluationReport);
        assertThat(second).isEqualTo(evaluationReport);
        verify(assignmentEventReportRepository, times(1)).findParticipantCountsByVariant(experiment.id(), reportWindow);
        verify(countableMetricEventReportRepository, times(1))
                .findMetricStatsByVariant(experiment.id(), metricDefinition.key(), reportWindow);
        verify(countableMetricReportAssembler, times(1))
                .assemble(eq(experiment), eq(metricDefinition), any(), any(), any(), eq(reportWindow));
        verify(experimentMetricRiskService, times(2)).getRisks(experiment.id(), metricDefinition.key());
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
                                "control",
                                new FeatureValue(true, FeatureValueType.BOOL),
                                0,
                                new BigDecimal("0.50"),
                                ExperimentVariantType.CONTROL),
                        new ExperimentVariant(
                                UUID.randomUUID(),
                                "treatment",
                                new FeatureValue(false, FeatureValueType.BOOL),
                                1,
                                new BigDecimal("0.50"),
                                ExperimentVariantType.REGULAR)),
                ExperimentState.RUNNING,
                3L,
                Instant.parse("2026-04-04T09:00:00Z"),
                null);
    }

    private MetricDefinition metricDefinition() {
        return new MetricDefinition(
                UUID.randomUUID(),
                "orders",
                "Orders",
                MetricType.COUNTABLE,
                MetricDirection.MORE_IS_BETTER,
                MetricSeverity.HIGH,
                new BigDecimal("0.10"));
    }

    private CountableMetricReport countableMetricReport(Experiment experiment, MetricDefinition metricDefinition) {
        return new CountableMetricReport(
                new ExperimentMetricReportMeta(
                        experiment.id(),
                        experiment.flagKey(),
                        metricDefinition.key(),
                        MetricType.COUNTABLE,
                        experiment.state(),
                        experiment.startedAt(),
                        experiment.completedAt(),
                        Instant.parse("2026-04-04T10:00:00Z"),
                        Instant.parse("2026-04-04T11:00:00Z")),
                220,
                65,
                88,
                new BigDecimal("0.2955"),
                new BigDecimal("0.4000"),
                List.of(
                        new CountableMetricReport.CountableVariantSummary(
                                experiment.variants().get(0).id(),
                                "control",
                                0,
                                100,
                                30,
                                40,
                                new BigDecimal("0.3000"),
                                new BigDecimal("0.4000")),
                        new CountableMetricReport.CountableVariantSummary(
                                experiment.variants().get(1).id(),
                                "treatment",
                                1,
                                120,
                                35,
                                48,
                                new BigDecimal("0.2917"),
                                new BigDecimal("0.4000"))));
    }

    private ExperimentMetricEvaluationReport evaluationReport() {
        return new ExperimentMetricEvaluationReport(null, null, List.of(), List.of());
    }
}
