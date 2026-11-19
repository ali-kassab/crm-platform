package com.crmplatform.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base class for expected, client-facing failures. The {@code code} is a stable machine-readable
 * identifier documented in VALIDATION_AND_ERROR_HANDLING.md; the message is safe to show users.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Map<String, Object> properties;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> properties) {
        super(message);
        this.status = status;
        this.code = code;
        this.properties = Map.copyOf(properties);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> properties() {
        return properties;
    }

    public static ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found",
                Map.of("resource", resource));
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    /** A body reference (e.g. {@code categoryId}) that does not exist in the active workspace. */
    public static ApiException referenceNotFound(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, "REFERENCE_NOT_FOUND",
                "Referenced record does not exist", Map.of("field", field));
    }
}
