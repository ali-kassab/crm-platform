package com.crmplatform.shared.web;

import com.crmplatform.shared.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds a safe {@link Pageable}: bounded page size and a whitelist of sortable fields mapped to
 * entity properties (prevents sorting/filtering on arbitrary or sensitive columns).
 */
public final class Pagination {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private Pagination() {
    }

    /**
     * @param sortParams values like {@code "createdAt,desc"}
     * @param allowed    API sort field → entity property
     * @param fallback   default sort when none requested
     */
    public static Pageable pageable(Integer page, Integer size, List<String> sortParams, Map<String, String> allowed,
            Sort fallback) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "page must be >= 0");
        }
        if (s < 1 || s > MAX_SIZE) {
            throw ApiException.badRequest("VALIDATION_ERROR", "size must be between 1 and " + MAX_SIZE);
        }
        List<Sort.Order> orders = new ArrayList<>();
        if (sortParams != null) {
            for (String raw : sortParams) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String[] parts = raw.split(",");
                String property = allowed.get(parts[0].trim());
                if (property == null) {
                    throw ApiException.badRequest("INVALID_SORT", "Sorting by '" + parts[0].trim() + "' is not supported");
                }
                boolean desc = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc");
                orders.add(desc ? Sort.Order.desc(property) : Sort.Order.asc(property));
            }
        }
        Sort sort = orders.isEmpty() ? fallback : Sort.by(orders);
        return PageRequest.of(p, s, sort);
    }

    /**
     * Normalises the search text ({@link SearchText}, Arabic-aware), escapes LIKE wildcards so it
     * is matched literally, and returns a contains-pattern. Compare it with
     * {@code app_search_norm(column)}, never with the raw column.
     */
    public static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String trimmed = q.trim();
        if (trimmed.length() > 100) {
            throw ApiException.badRequest("VALIDATION_ERROR", "q must be at most 100 characters");
        }
        String escaped = SearchText.normalize(trimmed).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
