package com.crmplatform.operations.jobs.application;

import com.crmplatform.operations.jobs.application.JobService.JobFilter;
import com.crmplatform.operations.jobs.domain.Job;
import com.crmplatform.shared.web.Pagination;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Whitelisted job filters (FR-OPS-02). */
final class JobSpecs {

    private JobSpecs() {
    }

    static Specification<Job> matching(JobFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.projectId() != null) {
                p.add(cb.equal(root.get("projectId"), f.projectId()));
            }
            if (f.tagId() != null) {
                p.add(cb.isMember(f.tagId(), root.<Collection<UUID>>get("tagIds")));
            }
            if (f.startFrom() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("startDate"), f.startFrom()));
            }
            if (f.startTo() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("startDate"), f.startTo()));
            }
            String like = Pagination.likePattern(f.q());
            if (like != null) {
                p.add(cb.or(cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("description")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
