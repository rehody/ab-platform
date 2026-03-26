package io.github.rehody.abplatform.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class FeatureFlagExceptionHandlerTest {

    private final FeatureFlagExceptionHandler featureFlagExceptionHandler = new FeatureFlagExceptionHandler();

    @Test
    void handleConstraintViolation_shouldReturnBadRequestAndMapConstraintViolationsWhenViolationsPresent() {
        Set<ConstraintViolation<?>> genericViolations = constraintViolationsForBlankKey();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/flags/flag-z");

        ResponseEntity<ErrorResponse> response = featureFlagExceptionHandler.handleConstraintViolation(
                new ConstraintViolationException(genericViolations), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.VALIDATION_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Validation error");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/flags/flag-z");
        assertThat(response.getBody().violations()).hasSize(1);
        assertThat(response.getBody().violations().get(0).field()).isEqualTo("key");
    }

    private Set<ConstraintViolation<?>> constraintViolationsForBlankKey() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            Set<ConstraintViolation<ValidationPayload>> violations = validator.validate(new ValidationPayload(""));
            Set<ConstraintViolation<?>> genericViolations = new HashSet<>();
            genericViolations.addAll(violations);
            return genericViolations;
        }
    }

    private record ValidationPayload(@NotBlank String key) {}
}
