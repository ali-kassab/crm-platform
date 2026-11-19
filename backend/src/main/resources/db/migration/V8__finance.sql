-- Finance: immutable money movements per job/project/contact; voiding is the only change (BR-090..094).

CREATE TABLE transactions (
    id                  uuid PRIMARY KEY,
    workspace_id        uuid           NOT NULL REFERENCES workspaces (id),
    job_id              uuid           NOT NULL REFERENCES jobs (id),
    project_id          uuid           NOT NULL REFERENCES projects (id),
    contact_id          uuid           NOT NULL REFERENCES contacts (id),
    type                varchar(8)     NOT NULL CHECK (type IN ('RECEIVED', 'PAID')),
    amount              numeric(19, 2) NOT NULL CHECK (amount > 0),
    title               varchar(255)   NOT NULL,
    payee               varchar(255)   NOT NULL,
    method              varchar(13)    NOT NULL CHECK (method IN ('CASH', 'CHECK', 'BANK_TRANSFER', 'CREDIT_CARD', 'OTHER')),
    bank_name           varchar(150),
    check_number        varchar(50),
    commitment_date     date           NOT NULL,
    due_date            date           NOT NULL,
    doc_notes           varchar(1000),
    doc_number          varchar(100),
    doc_date            date,
    attachment_file_id  uuid,
    voided_at           timestamptz,
    voided_by           uuid,
    void_reason         varchar(500),
    deleted_at          timestamptz,
    deleted_by          uuid,
    created_at          timestamptz    NOT NULL,
    created_by          uuid,
    updated_at          timestamptz    NOT NULL,
    updated_by          uuid,
    version             bigint         NOT NULL DEFAULT 0,
    CONSTRAINT ck_transactions_dates CHECK (due_date >= commitment_date),
    CONSTRAINT ck_transactions_bank CHECK (method NOT IN ('CHECK', 'BANK_TRANSFER', 'CREDIT_CARD') OR bank_name IS NOT NULL),
    CONSTRAINT ck_transactions_check CHECK (method <> 'CHECK' OR check_number IS NOT NULL),
    CONSTRAINT ck_transactions_void CHECK ((voided_at IS NULL) = (void_reason IS NULL))
);
CREATE INDEX ix_transactions_job ON transactions (job_id) INCLUDE (type, amount) WHERE voided_at IS NULL;
CREATE INDEX ix_transactions_project ON transactions (project_id) INCLUDE (type, amount) WHERE voided_at IS NULL;
CREATE INDEX ix_transactions_contact ON transactions (contact_id) INCLUDE (type, amount) WHERE voided_at IS NULL;
CREATE INDEX ix_transactions_ws_commitment ON transactions (workspace_id, commitment_date DESC);

-- Rejects any change except voiding once (the void columns plus bookkeeping columns).
CREATE FUNCTION transactions_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.voided_at IS NOT NULL THEN
        RAISE EXCEPTION 'transaction % is voided and cannot change', OLD.id USING ERRCODE = 'check_violation';
    END IF;
    IF (NEW.workspace_id, NEW.job_id, NEW.project_id, NEW.contact_id, NEW.type, NEW.amount, NEW.title, NEW.payee,
        NEW.method, NEW.bank_name, NEW.check_number, NEW.commitment_date, NEW.due_date, NEW.doc_notes,
        NEW.doc_number, NEW.doc_date, NEW.attachment_file_id, NEW.deleted_at, NEW.created_at, NEW.created_by)
       IS DISTINCT FROM
       (OLD.workspace_id, OLD.job_id, OLD.project_id, OLD.contact_id, OLD.type, OLD.amount, OLD.title, OLD.payee,
        OLD.method, OLD.bank_name, OLD.check_number, OLD.commitment_date, OLD.due_date, OLD.doc_notes,
        OLD.doc_number, OLD.doc_date, OLD.attachment_file_id, OLD.deleted_at, OLD.created_at, OLD.created_by) THEN
        RAISE EXCEPTION 'transactions are immutable; only voiding is allowed' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_transactions_immutable BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION transactions_immutable();
