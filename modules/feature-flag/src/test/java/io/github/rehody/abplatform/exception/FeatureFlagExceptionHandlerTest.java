package io.github.rehody.abplatform.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.HandlerMethod;

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
        assertThat(response.getBody().violations().getFirst().field()).isEqualTo("key");
    }

    @Test
    void handleOptimisticLockingFailure_shouldReturnConflictAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/flags/flag-z");

        ResponseEntity<ErrorResponse> response = featureFlagExceptionHandler.handleOptimisticLockingFailure(
                new OptimisticLockingFailureException("Feature flag 'flag-z' version mismatch. Expected version 2"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.CONFLICT);
        assertThat(response.getBody().message())
                .isEqualTo("Feature flag 'flag-z' version mismatch. Expected version 2");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/flags/flag-z");
    }

    @Test
    void handleNotFound_shouldReturnNotFoundAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/flags/flag-z");

        ResponseEntity<ErrorResponse> response = featureFlagExceptionHandler.handleNotFound(
                new FeatureFlagNotFoundException("Feature flag 'flag-z' not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Feature flag 'flag-z' not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/flags/flag-z");
    }

    @Test
    void handleAlreadyExists_shouldReturnConflictAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/flags");

        ResponseEntity<ErrorResponse> response = featureFlagExceptionHandler.handleAlreadyExists(
                new FeatureFlagAlreadyExistsException("Feature flag 'flag-a' already exists"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Feature flag 'flag-a' already exists");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/flags");
    }

    @Test
    void handleUpdateBlocked_shouldReturnConflictAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/flags/flag-a");

        ResponseEntity<ErrorResponse> response = featureFlagExceptionHandler.handleUpdateBlocked(
                new FeatureFlagUpdateBlockedException("Feature flag 'flag-a' default value cannot be updated"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Feature flag 'flag-a' default value cannot be updated");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/flags/flag-a");
    }

    @Test
    void handleLockAcquisition_shouldReturnConflictAndBusyMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/flags/flag-a");

        ResponseEntity<ErrorResponse> response = featureFlagExceptionHandler.handleLockAcquisition(
                new io.github.rehody.abplatform.util.lock.LockObtainingException("busy"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Feature flag is busy");
    }

    @Test
    void handleMethodArgumentNotValid_shouldReturnBadRequestAndViolations() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/flags");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new ValidationPayload(""), "request");
        bindingResult.addError(new FieldError("request", "key", "must not be blank"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new HandlerMethod(this, getClass().getDeclaredMethod("validationEndpoint", ValidationPayload.class))
                        .getMethodParameters()[0],
                bindingResult);

        ResponseEntity<ErrorResponse> response =
                featureFlagExceptionHandler.handleMethodArgumentNotValid(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().violations()).hasSize(1);
        assertThat(response.getBody().violations().getFirst().field()).isEqualTo("key");
        assertThat(response.getBody().violations().getFirst().message()).isEqualTo("must not be blank");
    }

    @Test
    void handleUnreadableMessage_shouldReturnBadRequestAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/flags");

        ResponseEntity<ErrorResponse> response = featureFlagExceptionHandler.handleUnreadableMessage(
                new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Invalid request body");
    }

    @Test
    void handleUnexpected_shouldReturnInternalServerErrorAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/flags");

        ResponseEntity<ErrorResponse> response =
                featureFlagExceptionHandler.handleUnexpected(new RuntimeException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Internal error");
    }

    private Set<ConstraintViolation<?>> constraintViolationsForBlankKey() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            Set<ConstraintViolation<ValidationPayload>> violations = validator.validate(new ValidationPayload(""));
            return new HashSet<>(violations);
        }
    }

    @SuppressWarnings("unused")
    private void validationEndpoint(ValidationPayload payload) {}

    private record ValidationPayload(@NotBlank String key) {}
}
