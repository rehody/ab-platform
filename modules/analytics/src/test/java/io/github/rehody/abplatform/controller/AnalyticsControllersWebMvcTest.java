package io.github.rehody.abplatform.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rehody.abplatform.binding.controller.ExperimentMetricBindingController;
import io.github.rehody.abplatform.binding.service.ExperimentMetricBindingService;
import io.github.rehody.abplatform.config.AbstractWebMvcTest;
import io.github.rehody.abplatform.conflict.controller.ExperimentConflictsController;
import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.conflict.enums.ExperimentConflictType;
import io.github.rehody.abplatform.conflict.model.ExperimentConflict;
import io.github.rehody.abplatform.conflict.service.ExperimentConflictResponseAssembler;
import io.github.rehody.abplatform.conflict.service.ExperimentConflictsService;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.evaluation.controller.ExperimentMetricEvaluationController;
import io.github.rehody.abplatform.evaluation.enums.MetricComparisonStatus;
import io.github.rehody.abplatform.evaluation.enums.TrafficStatus;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationMeta;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.service.ExperimentMetricEvaluationService;
import io.github.rehody.abplatform.event.controller.MetricEventController;
import io.github.rehody.abplatform.event.model.MetricEvent;
import io.github.rehody.abplatform.event.service.MetricEventService;
import io.github.rehody.abplatform.exception.AnalyticsExceptionHandler;
import io.github.rehody.abplatform.metric.controller.MetricDefinitionController;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.service.MetricDefinitionService;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.report.controller.ExperimentReportController;
import io.github.rehody.abplatform.report.model.CountableMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReportMeta;
import io.github.rehody.abplatform.report.model.UniqueMetricReport;
import io.github.rehody.abplatform.report.service.ExperimentReportService;
import io.github.rehody.abplatform.risk.enums.ExperimentMetricRiskStatus;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllersWebMvcTest extends AbstractWebMvcTest {

    private static final String ACTOR_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    private MetricDefinitionService metricDefinitionService;

    @Mock
    private MetricEventService metricEventService;

    @Mock
    private ExperimentMetricBindingService experimentMetricBindingService;

    @Mock
    private ExperimentReportService experimentReportService;

    @Mock
    private ExperimentMetricEvaluationService experimentMetricEvaluationService;

    @Mock
    private ExperimentConflictsService experimentConflictsService;

    private MockMvc metricDefinitionMockMvc;
    private MockMvc metricEventMockMvc;
    private MockMvc metricBindingMockMvc;
    private MockMvc experimentReportMockMvc;
    private MockMvc evaluationMockMvc;
    private MockMvc conflictsMockMvc;

    @BeforeEach
    void setUp() {
        AnalyticsExceptionHandler handler = new AnalyticsExceptionHandler();
        metricDefinitionMockMvc =
                buildStandaloneMockMvc(new MetricDefinitionController(metricDefinitionService), handler);
        metricEventMockMvc = buildStandaloneMockMvc(new MetricEventController(metricEventService), handler);
        metricBindingMockMvc =
                buildStandaloneMockMvc(new ExperimentMetricBindingController(experimentMetricBindingService), handler);
        experimentReportMockMvc =
                buildStandaloneMockMvc(new ExperimentReportController(experimentReportService), handler);
        evaluationMockMvc = buildStandaloneMockMvc(
                new ExperimentMetricEvaluationController(experimentMetricEvaluationService), handler);
        conflictsMockMvc = buildStandaloneMockMvc(
                new ExperimentConflictsController(
                        experimentConflictsService, new ExperimentConflictResponseAssembler()),
                handler);
    }

    @Test
    void metricDefinitionController_shouldCreateMetric() throws Exception {
        MetricDefinition metricDefinition = countableMetricDefinition();
        when(metricDefinitionService.create(
                        any(AuditActor.class),
                        eq("orders"),
                        eq("Orders"),
                        eq(MetricType.COUNTABLE),
                        eq(MetricDirection.MORE_IS_BETTER),
                        eq(MetricSeverity.HIGH),
                        eq(new BigDecimal("0.10"))))
                .thenReturn(metricDefinition);

        metricDefinitionMockMvc
                .perform(post("/api/v1/metrics")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "key":"orders",
                                  "name":"Orders",
                                  "type":"COUNTABLE",
                                  "direction":"MORE_IS_BETTER",
                                  "severity":"HIGH",
                                  "deviationThreshold":0.10
                                }
                                """)
                        .principal(() -> ACTOR_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("orders"))
                .andExpect(jsonPath("$.type").value("COUNTABLE"));
    }

    @Test
    void metricDefinitionController_shouldUpdateAndReadMetrics() throws Exception {
        MetricDefinition metricDefinition = uniqueMetricDefinition();
        when(metricDefinitionService.update(
                        any(AuditActor.class),
                        eq("orders"),
                        eq("Orders 2"),
                        eq(MetricType.UNIQUE),
                        eq(MetricDirection.LESS_IS_BETTER),
                        eq(MetricSeverity.MEDIUM),
                        eq(new BigDecimal("0.15"))))
                .thenReturn(metricDefinition);
        when(metricDefinitionService.getByKey("orders")).thenReturn(metricDefinition);
        when(metricDefinitionService.getAll()).thenReturn(List.of(metricDefinition));

        metricDefinitionMockMvc
                .perform(put("/api/v1/metrics/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Orders 2",
                                  "type":"UNIQUE",
                                  "direction":"LESS_IS_BETTER",
                                  "severity":"MEDIUM",
                                  "deviationThreshold":0.15
                                }
                                """)
                        .principal(() -> ACTOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("UNIQUE"));

        metricDefinitionMockMvc
                .perform(get("/api/v1/metrics/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(metricDefinition.id().toString()));

        metricDefinitionMockMvc
                .perform(get("/api/v1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("orders"));
    }

    @Test
    void metricEventController_shouldCreateMetricEvent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(metricEventService.create(userId, "orders"))
                .thenReturn(new MetricEvent(eventId, userId, "orders", Instant.parse("2026-04-05T10:00:00Z")));

        metricEventMockMvc
                .perform(post("/events/metrics").contentType(APPLICATION_JSON).content("""
                                {"userId":"%s","metricKey":"orders"}
                                """.formatted(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()));
    }

    @Test
    void bindingController_shouldUpdateAndReadBindings() throws Exception {
        UUID experimentId = UUID.randomUUID();
        when(experimentMetricBindingService.updateMetricKeys(
                        any(AuditActor.class), eq(experimentId), eq(List.of("orders", "revenue"))))
                .thenReturn(List.of("orders", "revenue"));
        when(experimentMetricBindingService.getMetricKeys(experimentId)).thenReturn(List.of("orders"));

        metricBindingMockMvc
                .perform(put("/api/v1/experiments/{experimentId}/metrics", experimentId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"metricKeys":["orders","revenue"]}
                                """)
                        .principal(() -> ACTOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experimentId").value(experimentId.toString()))
                .andExpect(jsonPath("$.metricKeys[1]").value("revenue"));

        metricBindingMockMvc
                .perform(get("/api/v1/experiments/{experimentId}/metrics", experimentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricKeys[0]").value("orders"));
    }

    @Test
    void reportController_shouldReturnCountableAndUniqueReports() throws Exception {
        UUID experimentId = UUID.randomUUID();
        when(experimentReportService.getExperimentReport(experimentId, "orders"))
                .thenReturn(countableMetricReport(experimentId));
        when(experimentReportService.getExperimentReport(experimentId, "signup"))
                .thenReturn(uniqueMetricReport(experimentId));

        experimentReportMockMvc
                .perform(get("/api/v1/reports/experiments/{experimentId}/metrics/{metricKey}", experimentId, "orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.metricKey").value("orders"))
                .andExpect(jsonPath("$.totalMetricEvents").value(22));

        experimentReportMockMvc
                .perform(get("/api/v1/reports/experiments/{experimentId}/metrics/{metricKey}", experimentId, "signup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.metricType").value("UNIQUE"))
                .andExpect(jsonPath("$.conversionRate").value(0.4));
    }

    @Test
    void evaluationController_shouldReturnEvaluationReport() throws Exception {
        UUID experimentId = UUID.randomUUID();
        when(experimentMetricEvaluationService.getEvaluationReport(experimentId, "orders"))
                .thenReturn(evaluationReport(experimentId, true));

        evaluationMockMvc
                .perform(get(
                        "/api/v1/reports/experiments/{experimentId}/metrics/{metricKey}/evaluation",
                        experimentId,
                        "orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.metricKey").value("orders"))
                .andExpect(jsonPath("$.traffic.status").value("WARNING"))
                .andExpect(jsonPath("$.comparisons[0].risk.status").value("OPEN"));
    }

    @Test
    void conflictsController_shouldReturnConflictsList() throws Exception {
        UUID experimentId = UUID.randomUUID();
        ExperimentConflict conflict = new ExperimentConflict(
                UUID.randomUUID(),
                ExperimentState.RUNNING,
                "flag-orders",
                "CHECKOUT",
                List.of(ExperimentConflictType.SAME_FLAG, ExperimentConflictType.DOMAIN_OVERLAP),
                ConflictSeverity.BLOCKING);
        when(experimentConflictsService.getAll(experimentId)).thenReturn(List.of(conflict));

        conflictsMockMvc
                .perform(get("/api/v1/experiments/{id}/conflicts", experimentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKING"))
                .andExpect(jsonPath("$.conflicts[0].flagKey").value("flag-orders"))
                .andExpect(jsonPath("$.conflicts[0].domainKey").value("CHECKOUT"))
                .andExpect(jsonPath("$.conflicts[0].conflictTypes[1]").value("DOMAIN_OVERLAP"));

        verify(experimentConflictsService).getAll(experimentId);
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
                "orders",
                "Orders 2",
                MetricType.UNIQUE,
                MetricDirection.LESS_IS_BETTER,
                MetricSeverity.MEDIUM,
                new BigDecimal("0.15"));
    }

    private CountableMetricReport countableMetricReport(UUID experimentId) {
        return new CountableMetricReport(
                reportMeta(experimentId, MetricType.COUNTABLE, "orders"),
                20,
                12,
                22,
                new BigDecimal("0.6000"),
                new BigDecimal("1.1000"),
                List.of(new CountableMetricReport.CountableVariantSummary(
                        UUID.randomUUID(),
                        "control",
                        0,
                        10,
                        5,
                        10,
                        new BigDecimal("0.5000"),
                        new BigDecimal("1.0000"))));
    }

    private UniqueMetricReport uniqueMetricReport(UUID experimentId) {
        return new UniqueMetricReport(
                reportMeta(experimentId, MetricType.UNIQUE, "signup"),
                20,
                8,
                new BigDecimal("0.4000"),
                List.of(new UniqueMetricReport.UniqueVariantSummary(
                        UUID.randomUUID(), "control", 0, 20, 8, new BigDecimal("0.4000"))));
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

    private ExperimentMetricEvaluationReport evaluationReport(UUID experimentId, boolean withRisk) {
        UUID controlVariantId = UUID.randomUUID();
        UUID treatmentVariantId = UUID.randomUUID();
        ExperimentMetricRisk risk = null;
        if (withRisk) {
            risk = new ExperimentMetricRisk(
                    UUID.randomUUID(),
                    experimentId,
                    "orders",
                    treatmentVariantId,
                    ExperimentMetricRiskStatus.OPEN,
                    Instant.parse("2026-04-05T10:00:00Z"),
                    null,
                    null,
                    Instant.parse("2026-04-05T10:05:00Z"),
                    new BigDecimal("0.11"),
                    new BigDecimal("0.12"),
                    null);
        }

        return new ExperimentMetricEvaluationReport(
                new ExperimentMetricEvaluationMeta(
                        experimentId,
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
                new ExperimentMetricEvaluationReport.TrafficEvaluation(
                        TrafficStatus.WARNING,
                        40,
                        List.of(new ExperimentMetricEvaluationReport.VariantTrafficShare(
                                controlVariantId,
                                "control",
                                0,
                                10,
                                new BigDecimal("0.5000"),
                                new BigDecimal("0.2500"),
                                new BigDecimal("0.2500")))),
                List.of(new ExperimentMetricEvaluationReport.VariantMetricAggregate(
                        controlVariantId, "control", 0, 10, 8, new BigDecimal("0.8000"))),
                List.of(new ExperimentMetricEvaluationReport.VariantComparison(
                        treatmentVariantId,
                        "treatment",
                        1,
                        MetricComparisonStatus.NEGATIVE_DEVIATION,
                        true,
                        false,
                        new BigDecimal("0.8000"),
                        new BigDecimal("0.6000"),
                        new BigDecimal("-0.2000"),
                        new BigDecimal("-0.2500"),
                        risk)));
    }
}
