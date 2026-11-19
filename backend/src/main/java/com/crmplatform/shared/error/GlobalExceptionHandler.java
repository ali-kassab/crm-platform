package com.crmplatform.shared.error;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every failure into RFC 9457 Problem Details (ADR-0011). Messages never contain
 * internals; unexpected errors only expose a trace id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://crm-platform.dev/problems/";

    private final ObjectProvider<Tracer> tracer;

    public GlobalExceptionHandler(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(ex.status(), ex.code(), ex.getMessage(), request);
        ex.properties().forEach(problem::setProperty);
        HttpHeaders headers = new HttpHeaders();
        Object retryAfter = ex.properties().get("retryAfter");
        if (retryAfter != null) {
            headers.add(HttpHeaders.RETRY_AFTER, retryAfter.toString());
        }
        return ResponseEntity.status(ex.status()).headers(headers).body(problem);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ProblemDetail> handleAccessDenied(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action", request));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(problem(HttpStatus.PRECONDITION_FAILED, "STALE_VERSION",
                        "The record was changed by someone else. Reload and try again.", request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> fieldError(lastNode(v.getPropertyPath().toString()), "INVALID", v.getMessage()))
                .toList();
        return validation(errors, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return validation(List.of(fieldError(ex.getName(), "INVALID_FORMAT", "has an invalid format")), request);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(baseProblem(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "The upload is too large",
                        path(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "An unexpected error occurred", request));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(fieldError(fe.getField(), codeFor(fe.getCode()), fe.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.add(fieldError(ge.getObjectName(), codeFor(ge.getCode()), ge.getDefaultMessage())));
        return ResponseEntity.badRequest().body(validationBody(errors, path(request)));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> fieldError(r.getMethodParameter().getParameterName(), "INVALID", e.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(validationBody(errors, path(request)));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail body = baseProblem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body is malformed", path(request));
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(baseProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", path(request)));
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail pd && pd.getProperties() == null) {
            pd.setProperty("code", HttpStatus.valueOf(statusCode.value()).name());
            pd.setProperty("traceId", traceId());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<ProblemDetail> validation(List<Map<String, String>> errors, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(validationBody(errors, request.getRequestURI()));
    }

    private ProblemDetail validationBody(List<Map<String, String>> errors, String path) {
        ProblemDetail problem = baseProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                errors.size() == 1 ? "1 field is invalid" : errors.size() + " fields are invalid", path);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        return baseProblem(status, code, detail, request.getRequestURI());
    }

    private ProblemDetail baseProblem(HttpStatus status, String code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase().replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        if (path != null) {
            problem.setInstance(URI.create(path));
        }
        problem.setProperty("code", code);
        problem.setProperty("traceId", traceId());
        return problem;
    }

    private String traceId() {
        return Optional.ofNullable(tracer.getIfAvailable())
                .map(Tracer::currentSpan)
                .map(span -> span.context().traceId())
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
    }

    private static Map<String, String> fieldError(String field, String code, String message) {
        return Map.of("field", field, "code", code, "message", message == null ? "is invalid" : message);
    }

    private static String codeFor(String constraint) {
        if (constraint == null) {
            return "INVALID";
        }
        return switch (constraint) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Size", "Length" -> "INVALID_LENGTH";
            case "Email" -> "EMAIL_INVALID";
            case "Pattern" -> "INVALID_FORMAT";
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero", "Digits" -> "OUT_OF_RANGE";
            default -> constraint.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
        };
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private static String path(WebRequest request) {
        String description = request.getDescription(false);
        return description.startsWith("uri=") ? description.substring(4) : null;
    }
}
