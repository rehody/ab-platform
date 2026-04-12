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
import io.github.rehody.abplatform.exception.ExperimentActivationConflictException;
import io.github.rehody.abplatform.exception.ExperimentBlockingConflictDetails;
import io.github.rehody.abplatform.exception.ExperimentBlockingConflictException;
import io.github.rehody.abplatform.exception.ExperimentExceptionHandler;
import io.github.rehody.abplatform.exception.ExperimentStateTransitionException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.service.ExperimentLifecycleService;
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

    private static final String ACTOR_ID = "11111111-1111-1111-1111-111111111111";

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
        when(experimentLifecycleService.submitForReview(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenReturn(experiment("flag-a", "CHECKOUT", 4L, ExperimentState.IN_REVIEW));
        when(experimentLifecycleService.approve(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenReturn(experiment("flag-a", "CHECKOUT", 5L, ExperimentState.APPROVED));
        when(experimentLifecycleService.reject(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenReturn(experiment("flag-a", "CHECKOUT", 5L, ExperimentState.REJECTED));
        when(experimentLifecycleService.start(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenReturn(experiment("flag-a", "CHECKOUT", 6L, ExperimentState.RUNNING));
        when(experimentLifecycleService.pause(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenReturn(experiment("flag-a", "CHECKOUT", 7L, ExperimentState.PAUSED));
        when(experimentLifecycleService.resume(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenReturn(experiment("flag-a", "CHECKOUT", 8L, ExperimentState.RUNNING));
        when(experimentLifecycleService.complete(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenReturn(experiment("flag-a", "CHECKOUT", 9L, ExperimentState.COMPLETED));
        when(experimentLifecycleService.archive(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenReturn(experiment("flag-a", "CHECKOUT", 10L, ExperimentState.ARCHIVED));

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
                        .principal(() -> ACTOR_ID)
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
        when(experimentLifecycleService.approve(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenThrow(new ExperimentStateTransitionException("Cannot approve experiment in state DRAFT"));

        mockMvc.perform(post("/api/v1/experiments/{id}/approve", id)
                        .principal(() -> ACTOR_ID)
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

    @Test
    void approve_shouldReturnConflictWithConflictingMetricKeysWhenActivationConflicts() throws Exception {
        UUID id = UUID.randomUUID();
        when(experimentLifecycleService.approve(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenThrow(new ExperimentActivationConflictException(
                        "Experiment '%s' conflicts with running experiments on metric keys: orders, revenue"
                                .formatted(id),
                        List.of("orders", "revenue")));

        mockMvc.perform(post("/api/v1/experiments/{id}/approve", id)
                        .principal(() -> ACTOR_ID)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"version":3}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("Experiment '%s' conflicts with running experiments on metric keys: orders, revenue"
                                .formatted(id)))
                .andExpect(jsonPath("$.violations[0].field").value("metricKeys"))
                .andExpect(jsonPath("$.violations[0].message").value("orders"))
                .andExpect(jsonPath("$.violations[1].field").value("metricKeys"))
                .andExpect(jsonPath("$.violations[1].message").value("revenue"))
                .andExpect(jsonPath("$.path").value("/api/v1/experiments/%s/approve".formatted(id)));
    }

    @Test
    void blockingConflictTransitions_shouldReturnConflictWithConflictingExperimentIds() throws Exception {
        UUID id = UUID.randomUUID();
        List<ExperimentBlockingConflictDetails> conflicts = List.of(
                new ExperimentBlockingConflictDetails(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        ExperimentState.RUNNING,
                        "flag-a",
                        "CHECKOUT",
                        List.of("SAME_FLAG"),
                        "BLOCKING"),
                new ExperimentBlockingConflictDetails(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        ExperimentState.RUNNING,
                        "flag-b",
                        "PRICING",
                        List.of("DOMAIN_OVERLAP"),
                        "BLOCKING"));
        List<String> conflictingExperimentIds = conflicts.stream()
                .map(conflict -> conflict.experimentId().toString())
                .toList();

        String message = "Experiment '%s' has blocking conflicts with running experiments: %s"
                .formatted(id, String.join(", ", conflictingExperimentIds));

        when(experimentLifecycleService.approve(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenThrow(new ExperimentBlockingConflictException(message, conflicts));
        when(experimentLifecycleService.start(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenThrow(new ExperimentBlockingConflictException(message, conflicts));
        when(experimentLifecycleService.resume(eq(id), eq(3L), eq(AuditActor.user(ACTOR_ID))))
                .thenThrow(new ExperimentBlockingConflictException(message, conflicts));

        assertBlockingConflictResponse("/api/v1/experiments/{id}/approve", id, conflicts);
        assertBlockingConflictResponse("/api/v1/experiments/{id}/start", id, conflicts);
        assertBlockingConflictResponse("/api/v1/experiments/{id}/resume", id, conflicts);
    }

    private void assertLifecycleResponse(String path, UUID id, String state, int version) throws Exception {
        mockMvc.perform(post(path, id)
                        .principal(() -> ACTOR_ID)
                        .contentType(APPLICATION_JSON)
                        .content("""
                        {"version":3}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value("flag-a"))
                .andExpect(jsonPath("$.domainKey").value("CHECKOUT"))
                .andExpect(jsonPath("$.variants[0].key").value("control"))
                .andExpect(jsonPath("$.state").value(state))
                .andExpect(jsonPath("$.version").value(version));
    }

    private void assertBlockingConflictResponse(String path, UUID id, List<ExperimentBlockingConflictDetails> conflicts)
            throws Exception {
        mockMvc.perform(post(path, id)
                        .principal(() -> ACTOR_ID)
                        .contentType(APPLICATION_JSON)
                        .content("""
                        {"version":3}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(jsonPath("$.violations[0].field").value("conflicts[0].experimentId"))
                .andExpect(jsonPath("$.violations[0].message")
                        .value(conflicts.get(0).experimentId().toString()))
                .andExpect(jsonPath("$.violations[1].field").value("conflicts[0].state"))
                .andExpect(jsonPath("$.violations[1].message").value("RUNNING"))
                .andExpect(jsonPath("$.violations[2].field").value("conflicts[0].flagKey"))
                .andExpect(jsonPath("$.violations[2].message")
                        .value(conflicts.get(0).flagKey()))
                .andExpect(jsonPath("$.violations[3].field").value("conflicts[0].domainKey"))
                .andExpect(jsonPath("$.violations[3].message")
                        .value(conflicts.get(0).domainKey()))
                .andExpect(jsonPath("$.violations[4].field").value("conflicts[0].severity"))
                .andExpect(jsonPath("$.violations[4].message").value("BLOCKING"))
                .andExpect(jsonPath("$.violations[5].field").value("conflicts[0].conflictTypes[0]"))
                .andExpect(jsonPath("$.violations[5].message")
                        .value(conflicts.get(0).conflictTypes().get(0)));
    }

    @SuppressWarnings("SameParameterValue")
    private Experiment experiment(String flagKey, String domainKey, long version, ExperimentState state) {
        return new Experiment(
                UUID.randomUUID(),
                flagKey,
                domainKey,
                ExperimentRolloutPlan.initial(),
                List.of(new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        null,
                        ExperimentVariantType.CONTROL)),
                state,
                version,
                null,
                null);
    }
}
