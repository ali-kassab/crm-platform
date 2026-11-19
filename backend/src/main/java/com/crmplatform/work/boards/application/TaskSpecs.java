package com.crmplatform.work.boards.application;

import com.crmplatform.shared.web.Pagination;
import com.crmplatform.work.boards.application.TaskService.TaskFilter;
import com.crmplatform.work.boards.domain.Task;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Whitelisted task filters (FR-WRK-17). Overdue = due before today and not done (TS-WRK-26). */
final class TaskSpecs {

    private TaskSpecs() {
    }

    static Specification<Task> matching(TaskFilter f, LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.projectId() != null) {
                p.add(cb.equal(root.get("projectId"), f.projectId()));
            }
            if (f.jobId() != null) {
                p.add(cb.equal(root.get("jobId"), f.jobId()));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), Task.Status.valueOf(f.status())));
            }
            if (f.priority() != null) {
                p.add(cb.equal(root.get("priority"), Task.Priority.valueOf(f.priority())));
            }
            if (f.assigneeId() != null) {
                p.add(cb.isMember(f.assigneeId(), root.<java.util.Collection<UUID>>get("assigneeIds")));
            }
            if (f.dueFrom() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("endDate"), f.dueFrom()));
            }
            if (f.dueTo() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("endDate"), f.dueTo()));
            }
            if (Boolean.TRUE.equals(f.overdue())) {
                p.add(cb.lessThan(root.get("endDate"), today));
                p.add(root.get("status").in(Task.Status.COMPLETED, Task.Status.CANCELLED).not());
            }
            String like = Pagination.likePattern(f.q());
            if (like != null) {
                p.add(cb.like(cb.lower(root.get("title")), like, '\\'));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
