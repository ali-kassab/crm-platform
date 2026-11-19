package com.crmplatform.shared.web;

import com.crmplatform.shared.error.ApiException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Binds a JSON object to a DTO, validates only the fields present, and records them. */
@Component
public class Patches {

    private final ObjectMapper mapper;
    private final Validator validator;

    public Patches(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public <T> Patch<T> read(JsonNode body, Class<T> type) {
        if (body == null || !body.isObject()) {
            throw ApiException.badRequest("MALFORMED_REQUEST", "The request body must be a JSON object");
        }
        T value;
        try {
            value = mapper.treeToValue(body, type);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("MALFORMED_REQUEST", "The request body is malformed");
        }
        Set<String> fields = new LinkedHashSet<>(body.propertyNames());
        List<Map<String, String>> errors = new ArrayList<>();
        for (ConstraintViolation<T> v : validator.validate(value)) {
            String path = v.getPropertyPath().toString();
            String root = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
            root = root.contains("[") ? root.substring(0, root.indexOf('[')) : root;
            if (!fields.contains(root)) {
                continue; // absent fields keep their current value and are not validated
            }
            boolean required = v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
                    .startsWith("Not");
            errors.add(Map.of("field", path, "code", required ? "REQUIRED" : "INVALID", "message", v.getMessage()));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    errors.size() == 1 ? "1 field is invalid" : errors.size() + " fields are invalid",
                    Map.of("errors", errors));
        }
        return new Patch<>(value, Set.copyOf(fields));
    }
}
