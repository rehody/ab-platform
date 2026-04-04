package io.github.rehody.abplatform.risk.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rehody.abplatform.config.AbstractWebMvcTest;
import io.github.rehody.abplatform.exception.AnalyticsExceptionHandler;
import io.github.rehody.abplatform.risk.enums.ExperimentMetricRiskStatus;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.risk.service.ExperimentMetricRiskService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class ExperimentMetricRiskControllerWebMvcTest extends AbstractWebMvcTest {

    @Mock
    private ExperimentMetricRiskService experimentMetricRiskService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildStandaloneMockMvc(
                new ExperimentMetricRiskController(experimentMetricRiskService), new AnalyticsExceptionHandler());
    }

    @Test
    void resolve_shouldAcceptOptionalBodyAndPassNullCommentToService() throws Exception {
        UUID riskId = UUID.randomUUID();
        when(experimentMetricRiskService.resolve(eq(riskId), isNull())).thenReturn(risk(riskId, null));

        mockMvc.perform(post("/api/v1/experiment-risks/{riskId}/resolve", riskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(riskId.toString()))
                .andExpect(jsonPath("$.resolutionComment").isEmpty());

        verify(experimentMetricRiskService).resolve(riskId, null);
    }

    @Test
    void resolve_shouldPassCommentToServiceWhenBodyProvided() throws Exception {
        UUID riskId = UUID.randomUUID();
        when(experimentMetricRiskService.resolve(eq(riskId), eq("manual review")))
                .thenReturn(risk(riskId, "manual review"));

        mockMvc.perform(post("/api/v1/experiment-risks/{riskId}/resolve", riskId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"comment":"manual review"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(riskId.toString()))
                .andExpect(jsonPath("$.resolutionComment").value("manual review"));

        verify(experimentMetricRiskService).resolve(riskId, "manual review");
    }

    private ExperimentMetricRisk risk(UUID riskId, String resolutionComment) {
        return new ExperimentMetricRisk(
                riskId,
                UUID.randomUUID(),
                "orders",
                UUID.randomUUID(),
                ExperimentMetricRiskStatus.RESOLVED,
                Instant.parse("2026-04-04T10:00:00Z"),
                Instant.parse("2026-04-04T11:00:00Z"),
                resolutionComment,
                Instant.parse("2026-04-04T10:55:00Z"),
                new BigDecimal("0.11"),
                new BigDecimal("0.14"),
                null);
    }
}
