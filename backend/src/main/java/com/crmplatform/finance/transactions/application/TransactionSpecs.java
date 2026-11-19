package com.crmplatform.finance.transactions.application;

import com.crmplatform.finance.transactions.application.TransactionService.TransactionFilter;
import com.crmplatform.finance.transactions.domain.Transaction;
import com.crmplatform.shared.web.Pagination;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Whitelisted transaction filters (FR-FIN-02); the date range applies to the commitment date. */
final class TransactionSpecs {

    private TransactionSpecs() {
    }

    static Specification<Transaction> matching(TransactionFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.type() != null) {
                p.add(cb.equal(root.get("type"), Transaction.Type.valueOf(f.type())));
            }
            if (f.method() != null) {
                p.add(cb.equal(root.get("method"), Transaction.Method.valueOf(f.method())));
            }
            if (f.jobId() != null) {
                p.add(cb.equal(root.get("jobId"), f.jobId()));
            }
            if (f.projectId() != null) {
                p.add(cb.equal(root.get("projectId"), f.projectId()));
            }
            if (f.contactId() != null) {
                p.add(cb.equal(root.get("contactId"), f.contactId()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("commitmentDate"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("commitmentDate"), f.to()));
            }
            if (!f.includeVoided()) {
                p.add(cb.isNull(root.get("voidedAt")));
            }
            String like = Pagination.likePattern(f.q());
            if (like != null) {
                p.add(cb.or(cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("payee")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
