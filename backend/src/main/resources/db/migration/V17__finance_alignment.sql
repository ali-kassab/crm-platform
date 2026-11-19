-- R8: money can be recorded on a project without a job (e.g. a fit-out down payment) or on a
-- standalone job (already possible since V12). Every transaction still needs a job or a project.
-- Reversible: rows with a job keep it; to undo, delete or reassign project-only rows, then
-- `ALTER TABLE transactions ALTER COLUMN job_id SET NOT NULL` and drop ck_transactions_parent.

ALTER TABLE transactions ALTER COLUMN job_id DROP NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_parent CHECK (job_id IS NOT NULL OR project_id IS NOT NULL);

-- outstanding-balances report: received per customer
CREATE INDEX IF NOT EXISTS ix_transactions_ws_contact ON transactions (workspace_id, contact_id)
    INCLUDE (type, amount) WHERE voided_at IS NULL AND deleted_at IS NULL;
