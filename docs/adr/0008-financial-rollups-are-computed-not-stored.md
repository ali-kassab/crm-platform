# ADR-0008: Financial rollups are computed, not stored

- Status: Accepted (2026-11-18)
- Contract, received, actual cost, remaining and P/L are computed with indexed aggregate queries over `transactions`, so they can never disagree with the ledger.
- Transactions are immutable. A correction is made by a **void** (NF-03): the voided row is excluded from the sums and the reason is recorded.

