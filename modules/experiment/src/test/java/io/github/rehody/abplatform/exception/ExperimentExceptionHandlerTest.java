package io.github.rehody.abplatform.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.dto.response.ErrorResponse;
import io.github.rehody.abplatform.util.lock.LockObtainingException;
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

class ExperimentExceptionHandlerTest {

    private final ExperimentExceptionHandler experimentExceptionHandler = new ExperimentExceptionHandler();

    @Test
    void handleConstraintViolation_shouldReturnBadRequestAndMapConstraintViolationsWhenViolationsPresent() {
        Set<ConstraintViolation<?>> genericViolations = constraintViolationsForBlankKey();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/experiments/flag-z");

        ResponseEntity<ErrorResponse> response = experimentExceptionHandler.handleConstraintViolation(
                new ConstraintViolationException(genericViolations), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.VALIDATION_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Validation error");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/experiments/flag-z");
        assertThat(response.getBody().violations()).hasSize(1);
        assertThat(response.getBody().violations().getFirst().field()).isEqualTo("key");
    }

    @Test
    void handleNotFound_shouldReturnNotFoundAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/experiments/123");

        ResponseEntity<ErrorResponse> response = experimentExceptionHandler.handleNotFound(
                new ExperimentNotFoundException("Experiment '123' not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Experiment '123' not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/experiments/123");
    }

    @Test
    void handleAlreadyExists_shouldReturnConflictAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/experiments");

        ResponseEntity<ErrorResponse> response = experimentExceptionHandler.handleAlreadyExists(
                new ExperimentAlreadyExistsException("Experiment with flag key 'flag-a' already exists"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Experiment with flag key 'flag-a' already exists");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/experiments");
    }

    @Test
    void handleLockAcquisition_shouldReturnConflictAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/experiments/123/approve");

        ResponseEntity<ErrorResponse> response =
                experimentExceptionHandler.handleLockAcquisition(new LockObtainingException("Lock timeout"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Experiment is busy");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/experiments/123/approve");
    }

    @Test
    void handleOptimisticLockingFailure_shouldReturnConflictAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/experiments/flag-z");

        ResponseEntity<ErrorResponse> response = experimentExceptionHandler.handleOptimisticLockingFailure(
                new OptimisticLockingFailureException("Experiment 'flag-z' version mismatch. Expected version 2"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Experiment 'flag-z' version mismatch. Expected version 2");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/experiments/flag-z");
    }

    @Test
    void handleStateTransitionFailure_shouldReturnConflictAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/experiments/123/approve");

        ResponseEntity<ErrorResponse> response = experimentExceptionHandler.handleStateTransitionFailure(
                new ExperimentStateTransitionException("Cannot approve experiment in state DRAFT"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Cannot approve experiment in state DRAFT");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/experiments/123/approve");
    }

    @Test
    void handleRolloutFailure_shouldReturnConflictAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/experiments/123/rollout");

        ResponseEntity<ErrorResponse> response = experimentExceptionHandler.handleRolloutFailure(
                new ExperimentRolloutException("Cannot advance experiment rollout"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Cannot advance experiment rollout");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/experiments/123/rollout");
    }

    @Test
    void handleBlockingConflict_shouldReturnConflictAndConflictingExperimentIds() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/experiments/123/start");

        ResponseEntity<ErrorResponse> response = experimentExceptionHandler.handleBlockingConflict(
                new ExperimentBlockingConflictException(
                        "Experiment '123' has blocking conflicts with running experiments: 1, 2",
                        java.util.List.of(
                                new ExperimentBlockingConflictDetails(
                                        java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
                                        io.github.rehody.abplatform.enums.ExperimentState.RUNNING,
                                        "flag-a",
                                        "CHECKOUT",
                                        java.util.List.of("SAME_FLAG", "DOMAIN_OVERLAP"),
                                        "BLOCKING"),
                                new ExperimentBlockingConflictDetails(
                                        java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"),
                                        io.github.rehody.abplatform.enums.ExperimentState.RUNNING,
                                        "flag-b",
                                        "PRICING",
                                        java.util.List.of("DOMAIN_OVERLAP"),
                                        "BLOCKING"))),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.CONFLICT);
        assertThat(response.getBody().message())
                .isEqualTo("Experiment '123' has blocking conflicts with running experiments: 1, 2");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/experiments/123/start");
        assertThat(response.getBody().violations()).hasSize(13);
        assertThat(response.getBody().violations().get(0).field()).isEqualTo("conflicts[0].experimentId");
        assertThat(response.getBody().violations().get(0).message()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(response.getBody().violations().get(1).field()).isEqualTo("conflicts[0].state");
        assertThat(response.getBody().violations().get(1).message()).isEqualTo("RUNNING");
        assertThat(response.getBody().violations().get(2).field()).isEqualTo("conflicts[0].flagKey");
        assertThat(response.getBody().violations().get(2).message()).isEqualTo("flag-a");
        assertThat(response.getBody().violations().get(3).field()).isEqualTo("conflicts[0].domainKey");
        assertThat(response.getBody().violations().get(3).message()).isEqualTo("CHECKOUT");
        assertThat(response.getBody().violations().get(4).field()).isEqualTo("conflicts[0].severity");
        assertThat(response.getBody().violations().get(4).message()).isEqualTo("BLOCKING");
        assertThat(response.getBody().violations().get(5).field()).isEqualTo("conflicts[0].conflictTypes[0]");
        assertThat(response.getBody().violations().get(5).message()).isEqualTo("SAME_FLAG");
        assertThat(response.getBody().violations().get(6).field()).isEqualTo("conflicts[0].conflictTypes[1]");
        assertThat(response.getBody().violations().get(6).message()).isEqualTo("DOMAIN_OVERLAP");
        assertThat(response.getBody().violations().get(7).field()).isEqualTo("conflicts[1].experimentId");
        assertThat(response.getBody().violations().get(7).message()).isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(response.getBody().violations().get(8).field()).isEqualTo("conflicts[1].state");
        assertThat(response.getBody().violations().get(8).message()).isEqualTo("RUNNING");
        assertThat(response.getBody().violations().get(9).field()).isEqualTo("conflicts[1].flagKey");
        assertThat(response.getBody().violations().get(9).message()).isEqualTo("flag-b");
        assertThat(response.getBody().violations().get(10).field()).isEqualTo("conflicts[1].domainKey");
        assertThat(response.getBody().violations().get(10).message()).isEqualTo("PRICING");
        assertThat(response.getBody().violations().get(11).field()).isEqualTo("conflicts[1].severity");
        assertThat(response.getBody().violations().get(11).message()).isEqualTo("BLOCKING");
        assertThat(response.getBody().violations().get(12).field()).isEqualTo("conflicts[1].conflictTypes[0]");
        assertThat(response.getBody().violations().get(12).message()).isEqualTo("DOMAIN_OVERLAP");
    }

    @Test
    void handleMethodArgumentNotValid_shouldReturnBadRequestAndViolations() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/experiments/123/approve");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new ValidationPayload(""), "request");
        bindingResult.addError(new FieldError("request", "version", "version is required"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new HandlerMethod(this, getClass().getDeclaredMethod("validationEndpoint", ValidationPayload.class))
                        .getMethodParameters()[0],
                bindingResult);

        ResponseEntity<ErrorResponse> response =
                experimentExceptionHandler.handleMethodArgumentNotValid(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.VALIDATION_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Validation error");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/experiments/123/approve");
        assertThat(response.getBody().violations()).hasSize(1);
        assertThat(response.getBody().violations().getFirst().field()).isEqualTo("version");
        assertThat(response.getBody().violations().getFirst().message()).isEqualTo("version is required");
    }

    @Test
    void handleUnreadableMessage_shouldReturnBadRequestAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/experiments/123/approve");

        ResponseEntity<ErrorResponse> response = experimentExceptionHandler.handleUnreadableMessage(
                new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Invalid request body");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/experiments/123/approve");
    }

    @Test
    void handleUnexpected_shouldReturnInternalServerErrorAndErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/experiments/123/approve");

        ResponseEntity<ErrorResponse> response =
                experimentExceptionHandler.handleUnexpected(new RuntimeException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Internal error");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/experiments/123/approve");
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
