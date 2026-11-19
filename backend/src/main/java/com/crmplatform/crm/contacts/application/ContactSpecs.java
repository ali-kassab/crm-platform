package com.crmplatform.crm.contacts.application;

import com.crmplatform.crm.contacts.application.ContactService.ContactFilter;
import com.crmplatform.crm.contacts.domain.Contact;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.SearchText;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Whitelisted contact filters (FR-CRM-05); search text is matched literally (EC-40). */
final class ContactSpecs {

    private ContactSpecs() {
    }

    static Specification<Contact> matching(ContactFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.type() != null) {
                p.add(cb.equal(root.get("type"), Contact.Type.valueOf(f.type())));
            }
            if (f.kind() != null) {
                p.add(cb.equal(root.get("kind"), Contact.Kind.valueOf(f.kind())));
            }
            if (f.statusId() != null) {
                p.add(cb.equal(root.get("statusId"), f.statusId()));
            }
            if (f.categoryId() != null) {
                p.add(cb.equal(root.get("categoryId"), f.categoryId()));
            }
            if (f.representativeId() != null) {
                p.add(cb.equal(root.get("representativeUserId"), f.representativeId()));
            }
            if (f.active() != null) {
                p.add(cb.equal(root.get("active"), f.active()));
            }
            if (f.tagId() != null) {
                p.add(cb.isMember(f.tagId(), root.<java.util.Collection<UUID>>get("tagIds")));
            }
            String like = Pagination.likePattern(f.q());
            if (like != null) {
                // names may be absent (company customers), so each part is coalesced before joining
                Expression<String> first = cb.coalesce(root.get("firstName"), "");
                Expression<String> last = cb.coalesce(root.get("lastName"), "");
                Expression<String> fullName = cb.concat(cb.concat(first, " "), last);
                p.add(cb.or(
                        cb.like(SearchText.column(cb, fullName), like, '\\'),
                        cb.like(SearchText.column(cb, cb.coalesce(root.get("email"), "")), like, '\\'),
                        cb.like(SearchText.column(cb, cb.coalesce(root.get("company"), "")), like, '\\'),
                        cb.like(cb.coalesce(root.get("phone"), ""), like, '\\'),
                        cb.like(cb.coalesce(root.get("mobile"), ""), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
