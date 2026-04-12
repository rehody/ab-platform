package io.github.rehody.abplatform.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import io.github.rehody.abplatform.binding.dto.request.ExperimentMetricsUpdateRequest;
import io.github.rehody.abplatform.binding.dto.response.ExperimentMetricsResponse;
import io.github.rehody.abplatform.cache.CachedCountableMetricReport;
import io.github.rehody.abplatform.cache.CachedExperimentMetricReport;
import io.github.rehody.abplatform.cache.CachedExperimentMetricReportMeta;
import io.github.rehody.abplatform.cache.CachedMetricDefinition;
import io.github.rehody.abplatform.cache.CachedUniqueMetricReport;
import io.github.rehody.abplatform.cache.ExperimentMetricReportCacheProperties;
import io.github.rehody.abplatform.cache.MetricDefinitionCacheProperties;
import io.github.rehody.abplatform.config.AnalyticsEvaluationProperties;
import io.github.rehody.abplatform.config.AnalyticsSchedulingConfig;
import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.evaluation.builder.ExperimentMetricEvaluationAssembler;
import io.github.rehody.abplatform.evaluation.builder.ExperimentMetricEvaluationMetaFactory;
import io.github.rehody.abplatform.evaluation.dto.response.ExperimentMetricEvaluationResponse;
import io.github.rehody.abplatform.evaluation.enums.MetricComparisonStatus;
import io.github.rehody.abplatform.evaluation.enums.TrafficStatus;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationMeta;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.TrafficEvaluation;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport.VariantComparison;
import io.github.rehody.abplatform.evaluation.service.ExperimentMetricEvaluationBatchService;
import io.github.rehody.abplatform.evaluation.service.ExperimentMetricEvaluationScheduler;
import io.github.rehody.abplatform.event.dto.request.MetricEventCreateRequest;
import io.github.rehody.abplatform.event.dto.response.MetricEventResponse;
import io.github.rehody.abplatform.event.model.AssignmentEvent;
import io.github.rehody.abplatform.event.model.MetricEvent;
import io.github.rehody.abplatform.exception.ExperimentReportUnavailableException;
import io.github.rehody.abplatform.metric.dto.request.MetricDefinitionCreateRequest;
import io.github.rehody.abplatform.metric.dto.request.MetricDefinitionUpdateRequest;
import io.github.rehody.abplatform.metric.dto.response.MetricDefinitionResponse;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.report.dto.response.CountableMetricReportResponse;
import io.github.rehody.abplatform.report.dto.response.ExperimentMetricReportResponse;
import io.github.rehody.abplatform.report.dto.response.UniqueMetricReportResponse;
import io.github.rehody.abplatform.report.factory.CountableMetricReportAssembler;
import io.github.rehody.abplatform.report.factory.ExperimentReportMetaFactory;
import io.github.rehody.abplatform.report.factory.ExperimentReportWindowFactory;
import io.github.rehody.abplatform.report.factory.UniqueMetricReportAssembler;
import io.github.rehody.abplatform.report.model.CountableMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReportMeta;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.model.UniqueMetricReport;
import io.github.rehody.abplatform.report.repository.aggregate.CountableMetricVariantAggregate;
import io.github.rehody.abplatform.report.repository.aggregate.UniqueMetricVariantAggregate;
import io.github.rehody.abplatform.risk.dto.response.ExperimentMetricRiskResponse;
import io.github.rehody.abplatform.risk.enums.ExperimentMetricRiskStatus;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsFactoryAndModelTest {

    @Mock
    private ExperimentMetricEvaluationBatchService experimentMetricEvaluationBatchService;

    @Test
    void countableMetricReportAssembler_shouldAssembleTotalsAndHandleMissingAggregates() {
        CountableMetricReportAssembler assembler =
                new CountableMetricReportAssembler(new ExperimentReportMetaFactory());
        Experiment experiment = runningExperiment();
        MetricDefinition metricDefinition = countableMetricDefinition();
        ExperimentReportWindow reportWindow = reportWindow();

        CountableMetricReport response = assembler.assemble(
                experiment,
                metricDefinition,
                orderedVariants(),
                Map.of(controlVariant().id(), 10, treatmentVariant().id(), 0),
                Map.of(
                        controlVariant().id(),
                        new CountableMetricVariantAggregate(controlVariant().id(), 4, 8)),
                reportWindow);

        assertThat(response.totalParticipants()).isEqualTo(10);
        assertThat(response.participantsWithMetricEvent()).isEqualTo(4);
        assertThat(response.totalMetricEvents()).isEqualTo(8);
        assertThat(response.participantConversionRate()).isEqualByComparingTo("0.4000");
        assertThat(response.eventsPerParticipant()).isEqualByComparingTo("0.8000");
        assertThat(response.variants().get(1).participantsWithMetricEvent()).isZero();
        assertThat(response.variants().get(1).eventsPerParticipant()).isEqualByComparingTo("0");
    }

    @Test
    void uniqueMetricReportAssembler_shouldAssembleTotalsAndHandleMissingAggregates() {
        UniqueMetricReportAssembler assembler = new UniqueMetricReportAssembler(new ExperimentReportMetaFactory());
        Experiment experiment = runningExperiment();
        MetricDefinition metricDefinition = uniqueMetricDefinition();
        ExperimentReportWindow reportWindow = reportWindow();

        UniqueMetricReport response = assembler.assemble(
                experiment,
                metricDefinition,
                orderedVariants(),
                Map.of(controlVariant().id(), 10, treatmentVariant().id(), 0),
                Map.of(
                        controlVariant().id(),
                        new UniqueMetricVariantAggregate(controlVariant().id(), 4)),
                reportWindow);

        assertThat(response.totalParticipants()).isEqualTo(10);
        assertThat(response.participantsWithMetricEvent()).isEqualTo(4);
        assertThat(response.conversionRate()).isEqualByComparingTo("0.4000");
        assertThat(response.variants().get(1).participantsWithMetricEvent()).isZero();
        assertThat(response.variants().get(1).conversionRate()).isEqualByComparingTo("0");
    }

    @Test
    void reportFactories_shouldMapExperimentAndMetricState() {
        Experiment experiment = runningExperiment();
        MetricDefinition metricDefinition = countableMetricDefinition();
        ExperimentReportWindow reportWindow = reportWindow();
        ExperimentReportMetaFactory metaFactory = new ExperimentReportMetaFactory();
        ExperimentMetricEvaluationMetaFactory evaluationMetaFactory = new ExperimentMetricEvaluationMetaFactory();

        ExperimentMetricReportMeta reportMeta = metaFactory.create(experiment, metricDefinition, reportWindow);
        ExperimentMetricEvaluationMeta evaluationMeta =
                evaluationMetaFactory.create(experiment, metricDefinition, reportWindow);

        assertThat(reportMeta.metricKey()).isEqualTo("orders");
        assertThat(reportMeta.experimentState()).isEqualTo(ExperimentState.RUNNING);
        assertThat(evaluationMeta.metricDirection()).isEqualTo(MetricDirection.MORE_IS_BETTER);
        assertThat(evaluationMeta.metricSeverity()).isEqualTo(MetricSeverity.HIGH);
    }

    @Test
    void experimentReportWindowFactory_shouldUseNowCompletedAtAndValidateBounds() {
        ExperimentReportWindowFactory factory = new ExperimentReportWindowFactory();
        Instant now = Instant.parse("2026-04-05T12:00:00Z");
        Experiment running = runningExperiment();
        Experiment completed = new Experiment(
                running.id(),
                running.flagKey(),
                running.domainKey(),
                running.rolloutPlan(),
                running.variants(),
                ExperimentState.COMPLETED,
                running.version(),
                running.startedAt(),
                Instant.parse("2026-04-05T11:00:00Z"));

        ExperimentReportWindow runningWindow = factory.create(running, now);
        ExperimentReportWindow completedWindow = factory.create(completed, now);

        assertThat(runningWindow.trackedTo()).isEqualTo(now);
        assertThat(completedWindow.trackedTo()).isEqualTo(completed.completedAt());

        Experiment notStarted = new Experiment(
                UUID.randomUUID(),
                "flag",
                "CHECKOUT",
                ExperimentRolloutPlan.initial(),
                running.variants(),
                ExperimentState.DRAFT,
                1L,
                null,
                null);
        assertThatThrownBy(() -> factory.create(notStarted, now))
                .isInstanceOf(ExperimentReportUnavailableException.class)
                .hasMessage("Experiment '%s' report is unavailable before start".formatted(notStarted.id()));

        Experiment invalidWindow = new Experiment(
                UUID.randomUUID(),
                "flag",
                "CHECKOUT",
                ExperimentRolloutPlan.initial(),
                running.variants(),
                ExperimentState.COMPLETED,
                1L,
                now,
                now.minusSeconds(1));
        assertThatThrownBy(() -> factory.create(invalidWindow, now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("report window is invalid");
    }

    @Test
    void evaluationAssembler_shouldBuildTrafficWarningsAndNegativeDeviation() {
        AnalyticsEvaluationProperties properties = evaluationProperties(5, 20, "0.05");
        ExperimentMetricEvaluationAssembler assembler =
                new ExperimentMetricEvaluationAssembler(new ExperimentMetricEvaluationMetaFactory(), properties);

        ExperimentMetricEvaluationReport response = assembler.assemble(
                balancedRolloutExperiment(),
                countableMetricDefinition(),
                orderedVariants(),
                Map.of(controlVariant().id(), 10, treatmentVariant().id(), 30),
                Map.of(
                        controlVariant().id(),
                        new CountableMetricVariantAggregate(controlVariant().id(), 8, 20),
                        treatmentVariant().id(),
                        new CountableMetricVariantAggregate(treatmentVariant().id(), 12, 30)),
                Map.of(treatmentVariant().id(), openRisk(treatmentVariant().id())),
                reportWindow());

        assertThat(response.traffic().status()).isEqualTo(TrafficStatus.WARNING);
        assertThat(response.traffic().variants().getFirst().shareDelta()).isEqualByComparingTo("0.2500");
        assertThat(response.comparisons()).hasSize(1);
        assertThat(response.comparisons().getFirst().status()).isEqualTo(MetricComparisonStatus.NEGATIVE_DEVIATION);
        assertThat(response.comparisons().getFirst().relativeDeviation()).isEqualByComparingTo("-0.5000");
        assertThat(response.comparisons().getFirst().risk()).isNotNull();
    }

    @Test
    void evaluationAssembler_shouldHandleInsufficientDataAndZeroControlCases() {
        AnalyticsEvaluationProperties insufficientProperties = evaluationProperties(50, 100, "0.50");
        ExperimentMetricEvaluationAssembler insufficientAssembler = new ExperimentMetricEvaluationAssembler(
                new ExperimentMetricEvaluationMetaFactory(), insufficientProperties);

        ExperimentMetricEvaluationReport insufficientResponse = insufficientAssembler.assemble(
                runningExperiment(),
                countableMetricDefinition(),
                orderedVariants(),
                Map.of(controlVariant().id(), 1, treatmentVariant().id(), 1),
                Map.of(
                        controlVariant().id(),
                        new CountableMetricVariantAggregate(controlVariant().id(), 1, 1),
                        treatmentVariant().id(),
                        new CountableMetricVariantAggregate(treatmentVariant().id(), 1, 1)),
                Map.of(),
                reportWindow());

        assertThat(insufficientResponse.traffic().status()).isEqualTo(TrafficStatus.INSUFFICIENT_DATA);
        assertThat(insufficientResponse.comparisons().getFirst().status())
                .isEqualTo(MetricComparisonStatus.INSUFFICIENT_DATA);

        AnalyticsEvaluationProperties zeroControlProperties = evaluationProperties(0, 1, "1.00");
        ExperimentMetricEvaluationAssembler zeroControlAssembler = new ExperimentMetricEvaluationAssembler(
                new ExperimentMetricEvaluationMetaFactory(), zeroControlProperties);

        ExperimentMetricEvaluationReport zeroControlResponse = zeroControlAssembler.assemble(
                runningExperiment(),
                uniqueMetricDefinition(),
                orderedVariants(),
                Map.of(controlVariant().id(), 10, treatmentVariant().id(), 10),
                Map.of(
                        controlVariant().id(),
                        new CountableMetricVariantAggregate(controlVariant().id(), 0, 0),
                        treatmentVariant().id(),
                        new CountableMetricVariantAggregate(treatmentVariant().id(), 5, 5)),
                Map.of(),
                reportWindow());

        assertThat(zeroControlResponse.comparisons().getFirst().zeroControl()).isTrue();
        assertThat(zeroControlResponse.comparisons().getFirst().status())
                .isEqualTo(MetricComparisonStatus.NEGATIVE_DEVIATION);
        assertThat(zeroControlResponse.comparisons().getFirst().relativeDeviation())
                .isEqualByComparingTo("0.5000");
    }

    @Test
    void evaluationAssembler_shouldRejectMissingControlVariant() {
        ExperimentMetricEvaluationAssembler assembler = new ExperimentMetricEvaluationAssembler(
                new ExperimentMetricEvaluationMetaFactory(), evaluationProperties(1, 1, "0.1"));

        assertThatThrownBy(() -> assembler.assemble(
                        runningExperiment(),
                        countableMetricDefinition(),
                        List.of(treatmentVariant()),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        reportWindow()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Control variant not found");
    }

    @Test
    void evaluationAssembler_shouldKeepBalancedTrafficAndStableMoreIsBetterMetricAsNormal() {
        ExperimentMetricEvaluationAssembler assembler = new ExperimentMetricEvaluationAssembler(
                new ExperimentMetricEvaluationMetaFactory(), evaluationProperties(1, 1, "0.10"));

        ExperimentMetricEvaluationReport response = assembler.assemble(
                balancedRolloutExperiment(),
                countableMetricDefinition(),
                orderedVariants(),
                Map.of(controlVariant().id(), 20, treatmentVariant().id(), 20),
                Map.of(
                        controlVariant().id(),
                        new CountableMetricVariantAggregate(controlVariant().id(), 10, 10),
                        treatmentVariant().id(),
                        new CountableMetricVariantAggregate(treatmentVariant().id(), 10, 10)),
                Map.of(),
                reportWindow());

        assertThat(response.traffic().status()).isEqualTo(TrafficStatus.NORMAL);
        assertThat(response.traffic().variants().getFirst().shareDelta()).isEqualByComparingTo("0.0000");
        assertThat(response.comparisons().getFirst().status()).isEqualTo(MetricComparisonStatus.NORMAL);
        assertThat(response.comparisons().getFirst().relativeDeviation()).isEqualByComparingTo("0.0000");
    }

    @Test
    void evaluationAssembler_shouldKeepLessIsBetterMetricNormalAtThreshold() {
        ExperimentMetricEvaluationAssembler assembler = new ExperimentMetricEvaluationAssembler(
                new ExperimentMetricEvaluationMetaFactory(), evaluationProperties(1, 1, "0.10"));

        ExperimentMetricEvaluationReport response = assembler.assemble(
                balancedRolloutExperiment(),
                uniqueMetricDefinition(),
                orderedVariants(),
                Map.of(controlVariant().id(), 20, treatmentVariant().id(), 20),
                Map.of(
                        controlVariant().id(),
                        new CountableMetricVariantAggregate(controlVariant().id(), 10, 10),
                        treatmentVariant().id(),
                        new CountableMetricVariantAggregate(treatmentVariant().id(), 11, 11)),
                Map.of(),
                reportWindow());

        assertThat(response.traffic().status()).isEqualTo(TrafficStatus.NORMAL);
        assertThat(response.comparisons().getFirst().zeroControl()).isFalse();
        assertThat(response.comparisons().getFirst().status()).isEqualTo(MetricComparisonStatus.NORMAL);
        assertThat(response.comparisons().getFirst().relativeDeviation()).isEqualByComparingTo("0.1000");
    }

    @Test
    void evaluationAssembler_shouldMarkComparisonAsInsufficientWhenTreatmentLacksEnoughEvents() {
        ExperimentMetricEvaluationAssembler assembler = new ExperimentMetricEvaluationAssembler(
                new ExperimentMetricEvaluationMetaFactory(), evaluationProperties(1, 1, "0.10"));

        ExperimentMetricEvaluationReport response = assembler.assemble(
                balancedRolloutExperiment(),
                countableMetricDefinition(),
                orderedVariants(),
                Map.of(controlVariant().id(), 20, treatmentVariant().id(), 20),
                Map.of(
                        controlVariant().id(),
                        new CountableMetricVariantAggregate(controlVariant().id(), 1, 1),
                        treatmentVariant().id(),
                        new CountableMetricVariantAggregate(treatmentVariant().id(), 0, 0)),
                Map.of(),
                reportWindow());

        assertThat(response.traffic().status()).isEqualTo(TrafficStatus.NORMAL);
        assertThat(response.comparisons().getFirst().status()).isEqualTo(MetricComparisonStatus.INSUFFICIENT_DATA);
    }

    @Test
    void responsesModelsAndEnums_shouldExposeHelperFlagsAndMappings() {
        MetricDefinition countableMetric = countableMetricDefinition();
        MetricDefinition uniqueMetric = uniqueMetricDefinition();
        ExperimentMetricRisk resolvedRisk = resolvedRisk();
        ExperimentMetricEvaluationReport evaluationReport = evaluationReportWithTwoComparisons();

        assertThat(countableMetric.isCountable()).isTrue();
        assertThat(uniqueMetric.isCountable()).isFalse();
        assertThat(evaluationReport.traffic().isNormal()).isTrue();
        assertThat(evaluationReport.comparisons().getFirst().hasNegativeDeviation())
                .isTrue();
        assertThat(evaluationReport.comparisons().get(1).hasNegativeDeviation()).isFalse();
        assertThat(resolvedRisk.isResolved()).isTrue();
        assertThat(openRisk(treatmentVariant().id()).isResolved()).isFalse();
        assertThat(ConflictSeverity.BLOCKING.isBlocking()).isTrue();
        assertThat(ConflictSeverity.WARNING.isWarning()).isTrue();
        assertThat(ConflictSeverity.NONE.isNone()).isTrue();
        assertThat(ConflictSeverity.NONE.isBlocking()).isFalse();
        assertThat(ConflictSeverity.BLOCKING.isWarning()).isFalse();
        assertThat(ConflictSeverity.WARNING.isNone()).isFalse();

        CountableMetricReport countableReport = countableReport();
        UniqueMetricReport uniqueReport = uniqueReport();
        ExperimentMetricReportResponse countableResponse = ExperimentMetricReportResponse.from(countableReport);
        ExperimentMetricReportResponse uniqueResponse = ExperimentMetricReportResponse.from(uniqueReport);
        MetricDefinitionResponse metricDefinitionResponse = MetricDefinitionResponse.from(countableMetric);
        MetricEvent metricEvent =
                new MetricEvent(UUID.randomUUID(), UUID.randomUUID(), "orders", Instant.parse("2026-04-05T10:00:00Z"));
        MetricEventResponse metricEventResponse = MetricEventResponse.from(metricEvent);
        ExperimentMetricRiskResponse nullRisk = ExperimentMetricRiskResponse.from(null);
        ExperimentMetricEvaluationResponse evaluationResponse =
                ExperimentMetricEvaluationResponse.from(evaluationReport);

        assertThat(countableResponse).isInstanceOf(CountableMetricReportResponse.class);
        assertThat(uniqueResponse).isInstanceOf(UniqueMetricReportResponse.class);
        assertThat(metricDefinitionResponse.key()).isEqualTo("orders");
        assertThat(metricEventResponse.eventId()).isEqualTo(metricEvent.id());
        assertThat(nullRisk).isNull();
        assertThat(evaluationResponse.comparisons()).hasSize(2);
        assertThat(evaluationResponse.comparisons().getFirst().risk()).isNotNull();
        assertThat(evaluationResponse.comparisons().get(1).risk()).isNull();

        assertThatThrownBy(() -> ExperimentMetricReportResponse.from((ExperimentMetricReport) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Experiment metric report must not be null");
    }

    @Test
    void cachedModels_shouldRoundTripAndRejectInvalidContainers() {
        MetricDefinition metricDefinition = countableMetricDefinition();
        CountableMetricReport countableReport = countableReport();
        UniqueMetricReport uniqueReport = uniqueReport();

        CachedMetricDefinition cachedMetricDefinition = CachedMetricDefinition.from(metricDefinition);
        CachedCountableMetricReport cachedCountableMetricReport = CachedCountableMetricReport.from(countableReport);
        CachedUniqueMetricReport cachedUniqueMetricReport = CachedUniqueMetricReport.from(uniqueReport);
        CachedExperimentMetricReportMeta cachedMeta = CachedExperimentMetricReportMeta.from(countableReport.meta());

        assertThat(cachedMetricDefinition.toModel()).isEqualTo(metricDefinition);
        assertThat(cachedCountableMetricReport.toModel()).isEqualTo(countableReport);
        assertThat(cachedUniqueMetricReport.toModel()).isEqualTo(uniqueReport);
        assertThat(cachedMeta.toModel()).isEqualTo(countableReport.meta());
        assertThat(CachedExperimentMetricReport.from(countableReport).toModel()).isEqualTo(countableReport);
        assertThat(CachedExperimentMetricReport.from(uniqueReport).toModel()).isEqualTo(uniqueReport);

        assertThatThrownBy(() -> new CachedExperimentMetricReport(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cached experiment metric report must contain exactly one report");
        assertThatThrownBy(
                        () -> new CachedExperimentMetricReport(cachedCountableMetricReport, cachedUniqueMetricReport))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cached experiment metric report must contain exactly one report");
        assertThatThrownBy(() -> CachedExperimentMetricReport.from((ExperimentMetricReport) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Experiment metric report must not be null");
    }

    @Test
    void propertiesRequestsAndScheduler_shouldExposeStateAndDelegate() {
        AnalyticsEvaluationProperties evaluationProperties = new AnalyticsEvaluationProperties();
        evaluationProperties.setEvaluationInterval(Duration.ofMinutes(5));
        evaluationProperties.setMinimumEventsForAnalysis(10);
        evaluationProperties.setMinimumParticipantsForTrafficWarning(20);
        evaluationProperties.setTrafficShareWarningThreshold(new BigDecimal("0.25"));

        MetricDefinitionCacheProperties metricDefinitionCacheProperties = new MetricDefinitionCacheProperties();
        metricDefinitionCacheProperties.setL1ValueTtl(Duration.ofMinutes(1));
        metricDefinitionCacheProperties.setL1MissTtl(Duration.ofSeconds(30));
        metricDefinitionCacheProperties.setL1ValueSize(10);
        metricDefinitionCacheProperties.setL1MissSize(5);
        metricDefinitionCacheProperties.setL2ValueTtl(Duration.ofMinutes(2));
        metricDefinitionCacheProperties.setL2MissTtl(Duration.ofMinutes(1));
        metricDefinitionCacheProperties.setTtlSpread(0.2d);
        metricDefinitionCacheProperties.setRedisKeyPrefix("metric");
        metricDefinitionCacheProperties.setInvalidationTopic("metric-topic");

        ExperimentMetricReportCacheProperties experimentMetricReportCacheProperties =
                new ExperimentMetricReportCacheProperties();
        experimentMetricReportCacheProperties.setL1ValueTtl(Duration.ofMinutes(1));
        experimentMetricReportCacheProperties.setL1MissTtl(Duration.ofSeconds(30));
        experimentMetricReportCacheProperties.setL1ValueSize(10);
        experimentMetricReportCacheProperties.setL1MissSize(5);
        experimentMetricReportCacheProperties.setL2ValueTtl(Duration.ofMinutes(2));
        experimentMetricReportCacheProperties.setL2MissTtl(Duration.ofMinutes(1));
        experimentMetricReportCacheProperties.setTtlSpread(0.2d);
        experimentMetricReportCacheProperties.setRedisKeyPrefix("report");
        experimentMetricReportCacheProperties.setInvalidationTopic("report-topic");

        MetricDefinitionCreateRequest createRequest = new MetricDefinitionCreateRequest(
                "orders",
                "Orders",
                MetricType.COUNTABLE,
                MetricDirection.MORE_IS_BETTER,
                MetricSeverity.HIGH,
                new BigDecimal("0.10"));
        MetricDefinitionUpdateRequest updateRequest = new MetricDefinitionUpdateRequest(
                "Orders 2",
                MetricType.UNIQUE,
                MetricDirection.LESS_IS_BETTER,
                MetricSeverity.MEDIUM,
                new BigDecimal("0.20"));
        MetricEventCreateRequest metricEventCreateRequest = new MetricEventCreateRequest(UUID.randomUUID(), "orders");
        ExperimentMetricsUpdateRequest metricsUpdateRequest = new ExperimentMetricsUpdateRequest(List.of("orders"));
        ExperimentMetricsResponse metricsResponse = new ExperimentMetricsResponse(UUID.randomUUID(), List.of("orders"));
        AssignmentEvent assignmentEvent = new AssignmentEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-04-05T10:00:00Z"));
        AnalyticsSchedulingConfig schedulingConfig = new AnalyticsSchedulingConfig();
        ExperimentMetricEvaluationScheduler scheduler =
                new ExperimentMetricEvaluationScheduler(experimentMetricEvaluationBatchService);

        assertThat(evaluationProperties.getEvaluationInterval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(evaluationProperties.getTrafficShareWarningThreshold()).isEqualByComparingTo("0.25");
        assertThat(metricDefinitionCacheProperties.getRedisKeyPrefix()).isEqualTo("metric");
        assertThat(experimentMetricReportCacheProperties.getRedisKeyPrefix()).isEqualTo("report");
        assertThat(createRequest.key()).isEqualTo("orders");
        assertThat(updateRequest.type()).isEqualTo(MetricType.UNIQUE);
        assertThat(metricEventCreateRequest.metricKey()).isEqualTo("orders");
        assertThat(metricsUpdateRequest.metricKeys()).containsExactly("orders");
        assertThat(metricsResponse.metricKeys()).containsExactly("orders");
        assertThat(assignmentEvent.experimentId()).isNotNull();
        assertThat(schedulingConfig).isNotNull();

        scheduler.evaluateRunningExperiments();

        verify(experimentMetricEvaluationBatchService).evaluateRunningExperiments();
    }

    private AnalyticsEvaluationProperties evaluationProperties(
            int minimumEventsForAnalysis, int minimumParticipantsForTrafficWarning, String warningThreshold) {
        AnalyticsEvaluationProperties properties = new AnalyticsEvaluationProperties();
        properties.setMinimumEventsForAnalysis(minimumEventsForAnalysis);
        properties.setMinimumParticipantsForTrafficWarning(minimumParticipantsForTrafficWarning);
        properties.setTrafficShareWarningThreshold(new BigDecimal(warningThreshold));
        return properties;
    }

    private Experiment runningExperiment() {
        return new Experiment(
                UUID.randomUUID(),
                "flag-orders",
                "CHECKOUT",
                ExperimentRolloutPlan.initial(),
                orderedVariants(),
                ExperimentState.RUNNING,
                2L,
                Instant.parse("2026-04-05T09:00:00Z"),
                null);
    }

    private Experiment balancedRolloutExperiment() {
        return new Experiment(
                UUID.randomUUID(),
                "flag-orders",
                "CHECKOUT",
                ExperimentRolloutPlan.of(50, false, false),
                orderedVariants(),
                ExperimentState.RUNNING,
                2L,
                Instant.parse("2026-04-05T09:00:00Z"),
                null);
    }

    private List<ExperimentVariant> orderedVariants() {
        return List.of(controlVariant(), treatmentVariant());
    }

    private ExperimentVariant controlVariant() {
        return new ExperimentVariant(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "control",
                new FeatureValue(true, FeatureValueType.BOOL),
                0,
                new BigDecimal("0.50"),
                ExperimentVariantType.CONTROL);
    }

    private ExperimentVariant treatmentVariant() {
        return new ExperimentVariant(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "treatment",
                new FeatureValue(false, FeatureValueType.BOOL),
                1,
                new BigDecimal("0.50"),
                ExperimentVariantType.REGULAR);
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
                MetricDirection.LESS_IS_BETTER,
                MetricSeverity.MEDIUM,
                new BigDecimal("0.10"));
    }

    private ExperimentReportWindow reportWindow() {
        return new ExperimentReportWindow(Instant.parse("2026-04-05T09:00:00Z"), Instant.parse("2026-04-05T10:00:00Z"));
    }

    private CountableMetricReport countableReport() {
        return new CountableMetricReport(
                reportMeta(MetricType.COUNTABLE, "orders"),
                20,
                8,
                12,
                new BigDecimal("0.4000"),
                new BigDecimal("0.6000"),
                List.of(new CountableMetricReport.CountableVariantSummary(
                        controlVariant().id(),
                        "control",
                        0,
                        10,
                        4,
                        6,
                        new BigDecimal("0.4000"),
                        new BigDecimal("0.6000"))));
    }

    private UniqueMetricReport uniqueReport() {
        return new UniqueMetricReport(
                reportMeta(MetricType.UNIQUE, "signup"),
                20,
                8,
                new BigDecimal("0.4000"),
                List.of(new UniqueMetricReport.UniqueVariantSummary(
                        controlVariant().id(), "control", 0, 10, 4, new BigDecimal("0.4000"))));
    }

    private ExperimentMetricReportMeta reportMeta(MetricType metricType, String metricKey) {
        return new ExperimentMetricReportMeta(
                UUID.randomUUID(),
                "flag-orders",
                metricKey,
                metricType,
                ExperimentState.RUNNING,
                Instant.parse("2026-04-05T09:00:00Z"),
                null,
                Instant.parse("2026-04-05T09:00:00Z"),
                Instant.parse("2026-04-05T10:00:00Z"));
    }

    private ExperimentMetricEvaluationReport evaluationReportWithTwoComparisons() {
        return new ExperimentMetricEvaluationReport(
                new ExperimentMetricEvaluationMeta(
                        UUID.randomUUID(),
                        "flag-orders",
                        "orders",
                        MetricDirection.MORE_IS_BETTER,
                        MetricSeverity.HIGH,
                        new BigDecimal("0.10"),
                        ExperimentState.RUNNING,
                        Instant.parse("2026-04-05T09:00:00Z"),
                        null,
                        Instant.parse("2026-04-05T09:00:00Z"),
                        Instant.parse("2026-04-05T10:00:00Z")),
                new TrafficEvaluation(TrafficStatus.NORMAL, 20, List.of()),
                List.of(new ExperimentMetricEvaluationReport.VariantMetricAggregate(
                        controlVariant().id(), "control", 0, 10, 8, new BigDecimal("0.8000"))),
                List.of(
                        new VariantComparison(
                                treatmentVariant().id(),
                                "treatment",
                                1,
                                MetricComparisonStatus.NEGATIVE_DEVIATION,
                                true,
                                false,
                                new BigDecimal("0.8"),
                                new BigDecimal("0.6"),
                                new BigDecimal("-0.2"),
                                new BigDecimal("-0.25"),
                                openRisk(treatmentVariant().id())),
                        new VariantComparison(
                                UUID.randomUUID(),
                                "other",
                                2,
                                MetricComparisonStatus.NORMAL,
                                true,
                                false,
                                new BigDecimal("0.8"),
                                new BigDecimal("0.8"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                null)));
    }

    private ExperimentMetricRisk openRisk(UUID variantId) {
        return new ExperimentMetricRisk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "orders",
                variantId,
                ExperimentMetricRiskStatus.OPEN,
                Instant.parse("2026-04-05T10:00:00Z"),
                null,
                null,
                Instant.parse("2026-04-05T10:05:00Z"),
                new BigDecimal("0.12"),
                new BigDecimal("0.12"),
                null);
    }

    private ExperimentMetricRisk resolvedRisk() {
        return new ExperimentMetricRisk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "orders",
                UUID.randomUUID(),
                ExperimentMetricRiskStatus.RESOLVED,
                Instant.parse("2026-04-05T10:00:00Z"),
                Instant.parse("2026-04-05T10:10:00Z"),
                "manual",
                Instant.parse("2026-04-05T10:10:00Z"),
                new BigDecimal("0.12"),
                new BigDecimal("0.15"),
                null);
    }
}
