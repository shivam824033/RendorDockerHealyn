package com.healyn.common.web;

import com.healyn.common.error.DomainException;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.logging.TraceContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(DomainException ex) {
        return respond(ex.status(), ex.code(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiErrorDetail(fe.getField(), normalize(fe.getCode())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiErrorDetail> details = ex.getConstraintViolations().stream()
                .map(this::violationDetail)
                .toList();
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformed(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request body is malformed.", List.of());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoHandler(NoHandlerFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Resource not found.", List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuth(AuthenticationException ex) {
        return respond(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Authentication required.", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Access denied.", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "The request conflicts with the current state of the resource.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL, "An unexpected error occurred.", List.of());
    }

    private ApiErrorDetail violationDetail(ConstraintViolation<?> cv) {
        String path = cv.getPropertyPath() == null ? "" : cv.getPropertyPath().toString();
        return new ApiErrorDetail(path, normalize(cv.getMessageTemplate()));
    }

    private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, String code, String message, List<ApiErrorDetail> details) {
        ApiError err = ApiError.of(code, message, details, TraceContext.currentTraceId());
        return ResponseEntity.status(status).body(new ApiErrorResponse(err));
    }

    private String normalize(String raw) {
        if (raw == null) return "INVALID";
        String trimmed = raw.replace("{jakarta.validation.constraints.", "").replace("}", "").replace(".message", "");
        return trimmed.toUpperCase();
    }
}
