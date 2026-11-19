package com.crmplatform.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crmplatform.shared.error.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PaginationTest {

    private static final Map<String, String> ALLOWED = Map.of("createdAt", "createdAt", "name", "lastName");
    private static final Sort FALLBACK = Sort.by("createdAt");

    private static Sort sortOf(List<String> params) {
        return Pagination.pageable(0, 20, params, ALLOWED, FALLBACK).getSort();
    }

    @Test
    void directionTokenSplitOffBySpringAppliesToThePreviousField() {
        // ?sort=createdAt,desc arrives as ["createdAt", "desc"]
        assertThat(sortOf(List.of("createdAt", "desc"))).isEqualTo(Sort.by(Sort.Order.desc("createdAt")));
        assertThat(sortOf(List.of("name", "asc", "createdAt", "DESC")))
                .isEqualTo(Sort.by(Sort.Order.asc("lastName"), Sort.Order.desc("createdAt")));
    }

    @Test
    void wholeValuesStillWork() {
        // ?sort=name,asc&sort=createdAt,desc keeps each value whole
        assertThat(sortOf(List.of("name,asc", "createdAt,desc")))
                .isEqualTo(Sort.by(Sort.Order.asc("lastName"), Sort.Order.desc("createdAt")));
        assertThat(sortOf(List.of())).isEqualTo(FALLBACK);
    }

    @Test
    void unknownFieldsAndLoneDirectionsAreRejected() {
        assertThatThrownBy(() -> sortOf(List.of("desc"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> sortOf(List.of("password", "desc"))).isInstanceOf(ApiException.class);
    }
}
