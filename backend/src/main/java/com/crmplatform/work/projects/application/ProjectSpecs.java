package com.crmplatform.work.projects.application;

import com.crmplatform.shared.web.Pagination;
import com.crmplatform.work.projects.application.ProjectService.ProjectFilter;
import com.crmplatform.work.projects.domain.Project;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Whitelisted project filters (FR-WRK-02). */
final class ProjectSpecs {

    private ProjectSpecs() {
    }

    static Specification<Project> matching(ProjectFilter f, Collection<UUID> restrictToIds) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.type() != null) {
                p.add(cb.equal(root.get("type"), Project.Type.valueOf(f.type())));
            }
            if (f.tagId() != null) {
                p.add(cb.isMember(f.tagId(), root.<Collection<UUID>>get("tagIds")));
            }
            if (f.locationId() != null) {
                p.add(cb.equal(root.get("locationId"), f.locationId()));
            }
            if (f.contactId() != null) {
                p.add(cb.equal(root.get("contactId"), f.contactId()));
            }
            if (f.startFrom() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("startDate"), f.startFrom()));
            }
            if (f.startTo() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("startDate"), f.startTo()));
            }
            String like = Pagination.likePattern(f.q());
            if (like != null) {
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("description")), like, '\\')));
            }
            if (restrictToIds != null) {
                p.add(root.get("id").in(restrictToIds));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
