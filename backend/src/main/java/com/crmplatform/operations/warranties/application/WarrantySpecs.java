package com.crmplatform.operations.warranties.application;

import com.crmplatform.operations.warranties.application.WarrantyService.WarrantyFilter;
import com.crmplatform.operations.warranties.domain.Warranty;
import com.crmplatform.shared.web.Pagination;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Whitelisted warranty filters (FR-OPS-06). */
final class WarrantySpecs {

    private WarrantySpecs() {
    }

    static Specification<Warranty> matching(WarrantyFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.projectId() != null) {
                p.add(cb.equal(root.get("projectId"), f.projectId()));
            }
            if (f.jobId() != null) {
                p.add(cb.equal(root.get("jobId"), f.jobId()));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), Warranty.Status.valueOf(f.status())));
            }
            if (f.provider() != null && !f.provider().isBlank()) {
                p.add(cb.like(cb.lower(root.get("provider")), Pagination.likePattern(f.provider()), '\\'));
            }
            if (f.endingBefore() != null) {
                p.add(cb.lessThan(root.get("endDate"), f.endingBefore()));
            }
            String like = Pagination.likePattern(f.q());
            if (like != null) {
                p.add(cb.like(cb.lower(root.get("title")), like, '\\'));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
