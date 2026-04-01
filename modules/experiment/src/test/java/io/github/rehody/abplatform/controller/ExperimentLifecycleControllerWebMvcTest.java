package io.github.rehody.abplatform.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rehody.abplatform.config.AbstractWebMvcTest;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.exception.ExperimentExceptionHandler;
import io.github.rehody.abplatform.exception.ExperimentStateTransitionException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.service.ExperimentLifecycleService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class ExperimentLifecycleControllerWebMvcTest extends AbstractWebMvcTest {

    @Mock
    private ExperimentLifecycleService experimentLifecycleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildStandaloneMockMvc(
                new ExperimentLifecycleController(experimentLifecycleService), new ExperimentExceptionHandler());
    }

    @Test
    void transitions_shouldReturnOkAndBodyForAllLifecycleEndpoints() throws Exception {
        UUID id = UUID.randomUUID();
        when(experimentLifecycleService.submitForReview(eq(id), eq(3L)))
                .thenReturn(experiment("flag-a", 4L, ExperimentState.IN_REVIEW));
        when(experimentLifecycleService.approve(eq(id), eq(3L)))
                .thenReturn(experiment("flag-a", 5L, ExperimentState.APPROVED));
        when(experimentLifecycleService.reject(eq(id), eq(3L)))
                .thenReturn(experiment("flag-a", 5L, ExperimentState.REJECTED));
        when(experimentLifecycleService.start(eq(id), eq(3L)))
                .thenReturn(experiment("flag-a", 6L, ExperimentState.RUNNING));
        when(experimentLifecycleService.pause(eq(id), eq(3L)))
                .thenReturn(experiment("flag-a", 7L, ExperimentState.PAUSED));
        when(experimentLifecycleService.resume(eq(id), eq(3L)))
                .thenReturn(experiment("flag-a", 8L, ExperimentState.RUNNING));
        when(experimentLifecycleService.complete(eq(id), eq(3L)))
                .thenReturn(experiment("flag-a", 9L, ExperimentState.COMPLETED));
        when(experimentLifecycleService.archive(eq(id), eq(3L)))
                .thenReturn(experiment("flag-a", 10L, ExperimentState.ARCHIVED));

        assertLifecycleResponse("/api/v1/experiments/{id}/submit-for-review", id, "IN_REVIEW", 4);
        assertLifecycleResponse("/api/v1/experiments/{id}/approve", id, "APPROVED", 5);
        assertLifecycleResponse("/api/v1/experiments/{id}/reject", id, "REJECTED", 5);
        assertLifecycleResponse("/api/v1/experiments/{id}/start", id, "RUNNING", 6);
        assertLifecycleResponse("/api/v1/experiments/{id}/pause", id, "PAUSED", 7);
        assertLifecycleResponse("/api/v1/experiments/{id}/resume", id, "RUNNING", 8);
        assertLifecycleResponse("/api/v1/experiments/{id}/complete", id, "COMPLETED", 9);
        assertLifecycleResponse("/api/v1/experiments/{id}/archive", id, "ARCHIVED", 10);
    }

    @Test
    void approve_shouldReturnBadRequestWhenRequestBodyInvalid() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/experiments/{id}/approve", id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"version":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/v1/experiments/%s/approve".formatted(id)));
    }

    @Test
    void approve_shouldReturnConflictWhenTransitionIsInvalid() throws Exception {
        UUID id = UUID.randomUUID();
        when(experimentLifecycleService.approve(eq(id), eq(3L)))
                .thenThrow(new ExperimentStateTransitionException("Cannot approve experiment in state DRAFT"));

        mockMvc.perform(post("/api/v1/experiments/{id}/approve", id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"version":3}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Cannot approve experiment in state DRAFT"))
                .andExpect(jsonPath("$.path").value("/api/v1/experiments/%s/approve".formatted(id)));
    }

    private void assertLifecycleResponse(String path, UUID id, String state, int version) throws Exception {
        mockMvc.perform(post(path, id).contentType(APPLICATION_JSON).content("""
                        {"version":3}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value("flag-a"))
                .andExpect(jsonPath("$.variants[0].key").value("control"))
                .andExpect(jsonPath("$.state").value(state))
                .andExpect(jsonPath("$.version").value(version));
    }

    @SuppressWarnings("SameParameterValue")
    private Experiment experiment(String flagKey, long version, ExperimentState state) {
        return new Experiment(
                UUID.randomUUID(),
                flagKey,
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE,
                        ExperimentVariantType.CONTROL)),
                state,
                version,
                null,
                null);
    }
}
