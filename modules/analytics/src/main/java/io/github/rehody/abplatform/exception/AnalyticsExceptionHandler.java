package io.github.rehody.abplatform.exception;

import io.github.rehody.abplatform.dto.response.ErrorResponse;
import io.github.rehody.abplatform.dto.response.ErrorResponse.ErrorCode;
import io.github.rehody.abplatform.dto.response.ErrorResponse.Violation;
import io.github.rehody.abplatform.util.lock.LockObtainingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AnalyticsExceptionHandler {

    @ExceptionHandler(MetricDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            MetricDefinitionNotFoundException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(ExperimentMetricRiskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRiskNotFound(
            ExperimentMetricRiskNotFoundException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(MetricEventAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(
            MetricEventAlreadyExistsException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.CONFLICT, ErrorCode.CONFLICT, ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(MetricDefinitionAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleMetricDefinitionAlreadyExists(
            MetricDefinitionAlreadyExistsException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.CONFLICT, ErrorCode.CONFLICT, ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(LockObtainingException.class)
    public ResponseEntity<ErrorResponse> handleLockAcquisition(LockObtainingException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Metric event is busy", request.getRequestURI(), List.of());
    }

    @ExceptionHandler(ExperimentReportUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleReportUnavailable(
            ExperimentReportUnavailableException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.CONFLICT, ErrorCode.CONFLICT, ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.CONFLICT,
                ErrorCode.CONFLICT,
                "Request conflicts with current data",
                request.getRequestURI(),
                List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Validation error",
                request.getRequestURI(),
                violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<Violation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new Violation(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Validation error",
                request.getRequestURI(),
                violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BAD_REQUEST,
                "Invalid request body",
                request.getRequestURI(),
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "Internal error",
                request.getRequestURI(),
                List.of());
    }

    private Violation toViolation(FieldError error) {
        return new Violation(error.getField(), Objects.toString(error.getDefaultMessage(), error.getCode()));
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, ErrorCode errorCode, String message, String path, List<Violation> violations) {
        ErrorResponse response = ErrorResponse.of(status.value(), errorCode, message, path, violations);
        return ResponseEntity.status(status).body(response);
    }
}
