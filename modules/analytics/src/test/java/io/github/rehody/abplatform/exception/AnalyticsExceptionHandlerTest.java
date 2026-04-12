package io.github.rehody.abplatform.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.dto.response.ErrorResponse;
import io.github.rehody.abplatform.risk.exception.ExperimentMetricRiskNotFoundException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.HandlerMethod;

class AnalyticsExceptionHandlerTest {

    private final AnalyticsExceptionHandler analyticsExceptionHandler = new AnalyticsExceptionHandler();

    @Test
    void handleNotFound_shouldReturnNotFoundResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/metrics/orders");

        ResponseEntity<ErrorResponse> response = analyticsExceptionHandler.handleNotFound(
                new MetricDefinitionNotFoundException("Metric definition 'orders' not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Metric definition 'orders' not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/metrics/orders");
    }

    @Test
    void handleRiskNotFound_shouldReturnNotFoundResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/experiment-risks/123");

        ResponseEntity<ErrorResponse> response = analyticsExceptionHandler.handleRiskNotFound(
                new ExperimentMetricRiskNotFoundException("Risk '123' not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Risk '123' not found");
    }

    @Test
    void handleAlreadyExists_shouldReturnConflictResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/events/metrics");

        ResponseEntity<ErrorResponse> response = analyticsExceptionHandler.handleAlreadyExists(
                new MetricEventAlreadyExistsException("Metric event already exists"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Metric event already exists");
    }

    @Test
    void handleMetricDefinitionAlreadyExists_shouldReturnConflictResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/metrics");

        ResponseEntity<ErrorResponse> response = analyticsExceptionHandler.handleMetricDefinitionAlreadyExists(
                new MetricDefinitionAlreadyExistsException("Metric definition 'orders' already exists"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Metric definition 'orders' already exists");
    }

    @Test
    void handleLockAcquisition_shouldReturnBusyMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/events/metrics");

        ResponseEntity<ErrorResponse> response =
                analyticsExceptionHandler.handleLockAcquisition(new LockObtainingException("busy"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Metric event is busy");
    }

    @Test
    void handleReportUnavailable_shouldReturnConflictResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/reports/experiments/1/metrics/orders");

        ResponseEntity<ErrorResponse> response = analyticsExceptionHandler.handleReportUnavailable(
                new ExperimentReportUnavailableException("Report unavailable"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Report unavailable");
    }

    @Test
    void handleIllegalArgument_shouldReturnBadRequestResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/experiments/1/metrics");

        ResponseEntity<ErrorResponse> response =
                analyticsExceptionHandler.handleIllegalArgument(new IllegalArgumentException("bad input"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("bad input");
    }

    @Test
    void handleDataIntegrityViolation_shouldReturnConflictResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/metrics");

        ResponseEntity<ErrorResponse> response = analyticsExceptionHandler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Request conflicts with current data");
    }

    @Test
    void handleMethodArgumentNotValid_shouldReturnValidationErrors() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/metrics");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new ValidationPayload(""), "request");
        bindingResult.addError(new FieldError("request", "key", "must not be blank"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new HandlerMethod(this, getClass().getDeclaredMethod("validationEndpoint", ValidationPayload.class))
                        .getMethodParameters()[0],
                bindingResult);

        ResponseEntity<ErrorResponse> response =
                analyticsExceptionHandler.handleMethodArgumentNotValid(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.VALIDATION_ERROR);
        assertThat(response.getBody().violations()).hasSize(1);
        assertThat(response.getBody().violations().getFirst().field()).isEqualTo("key");
        assertThat(response.getBody().violations().getFirst().message()).isEqualTo("must not be blank");
    }

    @Test
    void handleConstraintViolation_shouldReturnValidationErrors() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/metrics");

        ResponseEntity<ErrorResponse> response = analyticsExceptionHandler.handleConstraintViolation(
                new ConstraintViolationException(constraintViolationsForBlankKey()), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorResponse.ErrorCode.VALIDATION_ERROR);
        assertThat(response.getBody().violations()).hasSize(1);
        assertThat(response.getBody().violations().getFirst().field()).isEqualTo("key");
    }

    @Test
    void handleUnreadableMessage_shouldReturnBadRequestResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/metrics");

        ResponseEntity<ErrorResponse> response = analyticsExceptionHandler.handleUnreadableMessage(
                new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid request body");
    }

    @Test
    void handleUnexpected_shouldReturnInternalErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/metrics");

        ResponseEntity<ErrorResponse> response =
                analyticsExceptionHandler.handleUnexpected(new RuntimeException("boom"), request);

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
