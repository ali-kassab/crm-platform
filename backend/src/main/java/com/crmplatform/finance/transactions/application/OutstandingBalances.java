package com.crmplatform.finance.transactions.application;

import com.crmplatform.crm.Contacts;
import com.crmplatform.crm.Contacts.ContactSummary;
import com.crmplatform.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Who still owes us": per customer, contract value (project budgets + standalone job budgets)
 * minus money received, for customers with a positive balance. One aggregate query, scoped
 * explicitly to the caller's workspace (native SQL bypasses the tenant filter), largest first.
 * Uses the same rules as {@link AccountingService#contact}.
 */
@Service
public class OutstandingBalances {

    private static final String SQL = """
            with contract as (
                select contact_id, sum(budget) as amount from projects
                 where workspace_id = ? and deleted_at is null group by contact_id
                union all
                select contact_id, sum(budget) from jobs
                 where workspace_id = ? and deleted_at is null and project_id is null group by contact_id
            ), c as (
                select contact_id, sum(amount) as contract from contract group by contact_id
            ), r as (
                select contact_id,
                       coalesce(sum(amount) filter (where type = 'RECEIVED'), 0) as received,
                       max(commitment_date) filter (where type = 'RECEIVED') as last_payment
                  from transactions
                 where workspace_id = ? and voided_at is null and deleted_at is null
                 group by contact_id
            )
            select c.contact_id, c.contract, coalesce(r.received, 0) as received, r.last_payment,
                   count(*) over () as total
              from c left join r on r.contact_id = c.contact_id
             where c.contract - coalesce(r.received, 0) > 0
             order by c.contract - coalesce(r.received, 0) desc, c.contact_id
             limit ? offset ?
            """;

    private final JdbcTemplate jdbc;
    private final Contacts contacts;

    public OutstandingBalances(JdbcTemplate jdbc, Contacts contacts) {
        this.jdbc = jdbc;
        this.contacts = contacts;
    }

    @Transactional(readOnly = true)
    public Result list(int page, int size) {
        UUID ws = TenantContext.requireWorkspaceId();
        int limit = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page, 0) * limit;
        long[] total = {0};
        List<Row> rows = jdbc.query(SQL, (rs, i) -> {
            total[0] = rs.getLong("total");
            BigDecimal contract = rs.getBigDecimal("contract");
            BigDecimal received = rs.getBigDecimal("received");
            Date last = rs.getDate("last_payment");
            return new Row(rs.getObject("contact_id", UUID.class), null, null, contract, received,
                    contract.subtract(received), last == null ? null : last.toLocalDate());
        }, ws, ws, ws, limit, offset);
        Map<UUID, ContactSummary> names = contacts.findAll(rows.stream().map(Row::contactId).toList());
        List<Row> named = rows.stream().map(r -> {
            ContactSummary c = names.get(r.contactId());
            return c == null ? r : r.withCustomer(c.fullName(), c.phone() != null ? c.phone() : c.mobile());
        }).toList();
        BigDecimal sum = named.stream().map(Row::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Result(named, total[0], sum);
    }

    public record Row(UUID contactId, String customerName, String phone, BigDecimal contract, BigDecimal received,
            BigDecimal remaining, LocalDate lastPaymentDate) {
        Row withCustomer(String name, String phone) {
            return new Row(contactId, name, phone, contract, received, remaining, lastPaymentDate);
        }
    }

    /** {@code pageRemaining} is the sum of the rows on this page. */
    public record Result(List<Row> rows, long totalCustomers, BigDecimal pageRemaining) {
    }
}
