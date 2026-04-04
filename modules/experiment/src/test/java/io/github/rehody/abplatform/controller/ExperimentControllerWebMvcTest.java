package io.github.rehody.abplatform.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rehody.abplatform.config.AbstractWebMvcTest;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.enums.ExperimentVariantType;
import io.github.rehody.abplatform.exception.ExperimentExceptionHandler;
import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.service.ExperimentService;
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
class ExperimentControllerWebMvcTest extends AbstractWebMvcTest {

    @Mock
    private ExperimentService experimentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildStandaloneMockMvc(new ExperimentController(experimentService), new ExperimentExceptionHandler());
    }

    @Test
    void create_shouldReturnCreatedAndBodyWhenRequestIsValid() throws Exception {
        Experiment response = experiment("flag-a", "CHECKOUT", 0L, ExperimentState.DRAFT);
        when(experimentService.create(anyString(), anyString(), any(), any(ExperimentState.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/experiments")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"flagKey":"flag-a","domainKey":"CHECKOUT","variants":[{"id":"11111111-1111-1111-1111-111111111111","key":"control","value":{"value":true,"type":"BOOL"},"position":0,"weight":1,"type":"CONTROL"}],"state":"DRAFT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flagKey").value("flag-a"))
                .andExpect(jsonPath("$.domain").value("CHECKOUT"))
                .andExpect(jsonPath("$.variants[0].key").value("control"))
                .andExpect(jsonPath("$.variants[0].value.value").value(true))
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0));

        List<ExperimentVariant> expectedVariants = List.of(new ExperimentVariant(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "control",
                new FeatureValue(true, FeatureValueType.BOOL),
                0,
                BigDecimal.ONE,
                ExperimentVariantType.CONTROL));
        verify(experimentService).create("flag-a", "CHECKOUT", expectedVariants, ExperimentState.DRAFT);
    }

    @Test
    void update_shouldReturnOkAndBodyWhenRequestIsValid() throws Exception {
        UUID id = UUID.randomUUID();
        Experiment response = experiment(
                "flag-b",
                "PRICING",
                3L,
                ExperimentState.RUNNING,
                new ExperimentVariant(
                        UUID.randomUUID(),
                        "variant-a",
                        new FeatureValue("blue", FeatureValueType.STRING),
                        0,
                        BigDecimal.ONE,
                        ExperimentVariantType.REGULAR));
        when(experimentService.update(eq(id), any(), any(), any(), eq(2L))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/experiments/{id}", id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"domainKey":"PRICING","variants":[{"id":"11111111-1111-1111-1111-111111111111","key":"variant-a","value":{"value":"blue","type":"STRING"},"position":0,"weight":1,"type":"REGULAR"}],"version":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value("flag-b"))
                .andExpect(jsonPath("$.domain").value("PRICING"))
                .andExpect(jsonPath("$.variants[0].value.value").value("blue"))
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.version").value(3));

        List<ExperimentVariant> expectedVariants = List.of(new ExperimentVariant(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "variant-a",
                new FeatureValue("blue", FeatureValueType.STRING),
                0,
                BigDecimal.ONE,
                ExperimentVariantType.REGULAR));
        verify(experimentService).update(id, null, "PRICING", expectedVariants, 2L);
    }

    @Test
    void get_shouldReturnOkAndBodyWhenExperimentExists() throws Exception {
        UUID id = UUID.randomUUID();
        Experiment response = experiment("flag-c", "CHECKOUT", 4L, ExperimentState.APPROVED);
        when(experimentService.getById(id)).thenReturn(response);

        mockMvc.perform(get("/api/v1/experiments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value("flag-c"))
                .andExpect(jsonPath("$.domain").value("CHECKOUT"))
                .andExpect(jsonPath("$.variants[0].key").value("control"))
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void getAll_shouldReturnOkAndBodyWhenExperimentsExist() throws Exception {
        when(experimentService.getAll())
                .thenReturn(List.of(
                        experiment("flag-d", "CHECKOUT", 1L, ExperimentState.DRAFT),
                        experiment("flag-e", "PRICING", 2L, ExperimentState.ARCHIVED)));

        mockMvc.perform(get("/api/v1/experiments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flagKey").value("flag-d"))
                .andExpect(jsonPath("$[0].domain").value("CHECKOUT"))
                .andExpect(jsonPath("$[1].flagKey").value("flag-e"))
                .andExpect(jsonPath("$[1].domain").value("PRICING"))
                .andExpect(jsonPath("$[1].state").value("ARCHIVED"));
    }

    @Test
    void create_shouldReturnBadRequestWhenRequestBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/experiments")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"flagKey":" ","domainKey":" ","variants":null,"state":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/v1/experiments"));
    }

    @Test
    void update_shouldReturnBadRequestWhenRequestBodyInvalid() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/experiments/{id}", id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"variants":[{"id":"11111111-1111-1111-1111-111111111111","key":" ","value":null,"position":0,"weight":1}],"version":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/v1/experiments/%s".formatted(id)));
    }

    @Test
    void create_shouldReturnBadRequestWhenDomainIsUnknown() throws Exception {
        when(experimentService.create(anyString(), anyString(), any(), any(ExperimentState.class)))
                .thenThrow(new IllegalArgumentException("Unknown experiment domainKey 'UNKNOWN'"));

        mockMvc.perform(post("/api/v1/experiments")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"flagKey":"flag-a","domainKey":"UNKNOWN","variants":[{"id":"11111111-1111-1111-1111-111111111111","key":"control","value":{"value":true,"type":"BOOL"},"position":0,"weight":1,"type":"CONTROL"}],"state":"DRAFT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Unknown experiment domainKey 'UNKNOWN'"))
                .andExpect(jsonPath("$.path").value("/api/v1/experiments"));
    }

    @Test
    void get_shouldReturnNotFoundWhenExperimentMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(experimentService.getById(id))
                .thenThrow(new ExperimentNotFoundException("Experiment '%s' not found".formatted(id)));

        mockMvc.perform(get("/api/v1/experiments/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Experiment '%s' not found".formatted(id)))
                .andExpect(jsonPath("$.path").value("/api/v1/experiments/%s".formatted(id)));
    }

    private Experiment experiment(String flagKey, String domain, long version, ExperimentState state) {
        return experiment(
                flagKey,
                domain,
                version,
                state,
                new ExperimentVariant(
                        UUID.randomUUID(),
                        "control",
                        new FeatureValue(true, FeatureValueType.BOOL),
                        0,
                        BigDecimal.ONE,
                        ExperimentVariantType.CONTROL));
    }

    private Experiment experiment(
            String flagKey, String domain, long version, ExperimentState state, ExperimentVariant variant) {
        return new Experiment(UUID.randomUUID(), flagKey, domain, List.of(variant), state, version, null, null);
    }
}
