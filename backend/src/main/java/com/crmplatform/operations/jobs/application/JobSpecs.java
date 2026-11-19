package com.crmplatform.operations.jobs.application;

import com.crmplatform.operations.jobs.application.JobService.JobFilter;
import com.crmplatform.operations.jobs.domain.Job;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.SearchText;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Whitelisted job filters (FR-OPS-02, directive §5C). */
final class JobSpecs {

    private JobSpecs() {
    }

    static Specification<Job> matching(JobFilter f, java.time.Instant now) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (Boolean.TRUE.equals(f.slaBreached())) {
                var due = root.<java.time.Instant>get("responseDueAt");
                var started = root.<java.time.Instant>get("actualStartAt");
                p.add(cb.isNotNull(due));
                p.add(cb.or(cb.and(cb.isNull(started), cb.lessThan(due, now)), cb.greaterThan(started, due)));
            }
            if (f.projectId() != null) {
                p.add(cb.equal(root.get("projectId"), f.projectId()));
            }
            if (Boolean.TRUE.equals(f.standalone())) {
                p.add(cb.isNull(root.get("projectId")));
            } else if (Boolean.FALSE.equals(f.standalone())) {
                p.add(cb.isNotNull(root.get("projectId")));
            }
            if (f.contactId() != null) {
                p.add(cb.equal(root.get("contactId"), f.contactId()));
            }
            if (f.locationId() != null) {
                p.add(cb.equal(root.get("locationId"), f.locationId()));
            }
            if (f.type() != null) {
                p.add(cb.equal(root.get("type"), JobService.parseType(f.type())));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), JobService.parseStatus(f.status())));
            }
            if (f.priority() != null) {
                p.add(cb.equal(root.get("priority"), JobService.parsePriority(f.priority())));
            }
            if (f.assigneeUserId() != null) {
                p.add(cb.isMember(f.assigneeUserId(), root.<Collection<UUID>>get("assigneeUserIds")));
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
                p.add(cb.or(cb.like(SearchText.column(cb, root.get("title")), like, '\\'),
                        cb.like(SearchText.column(cb, root.get("description")), like, '\\'),
                        cb.like(SearchText.column(cb, cb.coalesce(root.get("reportedIssue"), "")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
