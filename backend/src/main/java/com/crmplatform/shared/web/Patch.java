package com.crmplatform.shared.web;

import java.util.Set;
import java.util.function.Consumer;

/**
 * A validated partial update: the bound DTO plus the set of JSON fields actually present. A
 * present field with value {@code null} means "clear"; an absent field means "leave unchanged"
 * (BR-004).
 */
public record Patch<T>(T value, Set<String> fields) {

    public boolean has(String field) {
        return fields.contains(field);
    }

    /** Applies {@code setter} with the new value only if the field was sent. */
    public <V> void ifPresent(String field, V newValue, Consumer<V> setter) {
        if (has(field)) {
            setter.accept(newValue);
        }
    }
}
