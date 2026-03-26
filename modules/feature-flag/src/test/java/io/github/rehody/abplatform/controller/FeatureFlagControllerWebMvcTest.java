package io.github.rehody.abplatform.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rehody.abplatform.config.AbstractWebMvcTest;
import io.github.rehody.abplatform.dto.response.FeatureFlagResponse;
import io.github.rehody.abplatform.exception.FeatureFlagAlreadyExistsException;
import io.github.rehody.abplatform.exception.FeatureFlagExceptionHandler;
import io.github.rehody.abplatform.exception.FeatureFlagNotFoundException;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.service.FeatureFlagService;
import io.github.rehody.abplatform.util.lock.LockObtainingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class FeatureFlagControllerWebMvcTest extends AbstractWebMvcTest {

    @Mock
    private FeatureFlagService featureFlagService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildStandaloneMockMvc(
                new FeatureFlagController(featureFlagService), new FeatureFlagExceptionHandler());
    }

    @Test
    void create_shouldReturnOkAndBodyWhenRequestIsValid() throws Exception {
        FeatureFlagResponse response = new FeatureFlagResponse("flag-a", new FeatureValue(true, FeatureValueType.BOOL));
        when(featureFlagService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON).content("""
                                {"key":"flag-a","defaultValue":{"value":true,"type":"BOOL"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("flag-a"))
                .andExpect(jsonPath("$.defaultValue.value").value(true))
                .andExpect(jsonPath("$.defaultValue.type").value("BOOL"));
    }

    @Test
    void update_shouldReturnOkAndBodyWhenRequestIsValid() throws Exception {
        FeatureFlagResponse response =
                new FeatureFlagResponse("flag-b", new FeatureValue("variant-a", FeatureValueType.STRING));
        when(featureFlagService.update(eq("flag-b"), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/flags/flag-b")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"defaultValue":{"value":"variant-a","type":"STRING"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("flag-b"))
                .andExpect(jsonPath("$.defaultValue.value").value("variant-a"))
                .andExpect(jsonPath("$.defaultValue.type").value("STRING"));
    }

    @Test
    void get_shouldReturnOkAndBodyWhenFeatureFlagExists() throws Exception {
        FeatureFlagResponse response = new FeatureFlagResponse("flag-c", new FeatureValue(12, FeatureValueType.NUMBER));
        when(featureFlagService.getByKey("flag-c")).thenReturn(response);

        mockMvc.perform(get("/api/v1/flags/flag-c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("flag-c"))
                .andExpect(jsonPath("$.defaultValue.value").value(12))
                .andExpect(jsonPath("$.defaultValue.type").value("NUMBER"));
    }

    @Test
    void create_shouldReturnBadRequestAndValidationErrorsWhenRequestBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON).content("""
                                {"key":" ","defaultValue":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/v1/flags"));
    }

    @Test
    void create_shouldReturnConflictAndErrorResponseWhenFeatureFlagAlreadyExists() throws Exception {
        when(featureFlagService.create(any()))
                .thenThrow(new FeatureFlagAlreadyExistsException("Feature flag 'flag-d' already exists"));

        mockMvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON).content("""
                                {"key":"flag-d","defaultValue":{"value":true,"type":"BOOL"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Feature flag 'flag-d' already exists"))
                .andExpect(jsonPath("$.path").value("/api/v1/flags"));
    }

    @Test
    void update_shouldReturnConflictAndErrorResponseWhenLockCannotBeObtained() throws Exception {
        when(featureFlagService.update(eq("flag-e"), any())).thenThrow(new LockObtainingException("Lock timeout"));

        mockMvc.perform(put("/api/v1/flags/flag-e")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"defaultValue":{"value":true,"type":"BOOL"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Feature flag is busy"))
                .andExpect(jsonPath("$.path").value("/api/v1/flags/flag-e"));
    }

    @Test
    void get_shouldReturnNotFoundAndErrorResponseWhenFeatureFlagMissing() throws Exception {
        when(featureFlagService.getByKey("flag-f"))
                .thenThrow(new FeatureFlagNotFoundException("Feature flag 'flag-f' not found"));

        mockMvc.perform(get("/api/v1/flags/flag-f"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Feature flag 'flag-f' not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/flags/flag-f"));
    }

    @Test
    void create_shouldReturnBadRequestAndErrorResponseWhenRequestBodyUnreadable() throws Exception {
        mockMvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON).content("""
                                {"key":"flag-g","defaultValue":{"value":true,"type":"UNKNOWN"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request body"))
                .andExpect(jsonPath("$.path").value("/api/v1/flags"));
    }

    @Test
    void get_shouldReturnBadRequestAndValidationErrorWhenConstraintViolationThrown() throws Exception {
        Set<ConstraintViolation<?>> genericViolations = constraintViolationsForBlankKey();
        when(featureFlagService.getByKey("flag-h")).thenThrow(new ConstraintViolationException(genericViolations));

        mockMvc.perform(get("/api/v1/flags/flag-h"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations[0].field").value("key"));
    }

    @Test
    void get_shouldReturnInternalServerErrorAndErrorResponseWhenUnexpectedExceptionThrown() throws Exception {
        when(featureFlagService.getByKey("flag-i")).thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/api/v1/flags/flag-i"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal error"))
                .andExpect(jsonPath("$.path").value("/api/v1/flags/flag-i"));
    }

    private Set<ConstraintViolation<?>> constraintViolationsForBlankKey() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            Set<ConstraintViolation<ValidationInput>> violations = validator.validate(new ValidationInput(""));
            return new HashSet<>(violations);
        }
    }

    private record ValidationInput(@NotBlank String key) {}
}
