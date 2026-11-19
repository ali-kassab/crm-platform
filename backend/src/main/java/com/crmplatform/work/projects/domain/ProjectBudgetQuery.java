package com.crmplatform.work.projects.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Reads only {@code (id, budget)} for the projects matching a specification: one two-column query,
 * no entity hydration and no element-collection loads. Tenant filtering still applies (Hibernate
 * adds the workspace predicate to every query on a tenant entity).
 */
@Component
public class ProjectBudgetQuery {

    private final EntityManager em;

    public ProjectBudgetQuery(EntityManager em) {
        this.em = em;
    }

    public Map<UUID, BigDecimal> budgets(Specification<Project> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Project> root = query.from(Project.class);
        var predicate = spec.toPredicate(root, query, cb);
        query.multiselect(root.get("id").alias("id"), root.get("budget").alias("budget"));
        if (predicate != null) {
            query.where(predicate);
        }
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        for (Tuple t : em.createQuery(query).getResultList()) {
            result.put(t.get("id", UUID.class), t.get("budget", BigDecimal.class));
        }
        return result;
    }
}
