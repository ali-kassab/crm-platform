package com.crmplatform.finance.transactions.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    /** Rows of {type, Σ amount, count} over non-voided transactions of a job (BR-094). */
    @Query("select t.type, sum(t.amount), count(t) from Transaction t where t.jobId = :id and t.voidedAt is null "
            + "group by t.type")
    List<Object[]> totalsByJob(UUID id);

    @Query("select t.type, sum(t.amount), count(t) from Transaction t where t.projectId = :id and t.voidedAt is null "
            + "group by t.type")
    List<Object[]> totalsByProject(UUID id);

    @Query("select t.type, sum(t.amount), count(t) from Transaction t where t.contactId = :id and t.voidedAt is null "
            + "group by t.type")
    List<Object[]> totalsByContact(UUID id);
}
