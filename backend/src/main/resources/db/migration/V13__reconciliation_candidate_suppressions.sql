CREATE TABLE reconciliation_candidate_suppression (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    financial_account_id UUID NOT NULL,
    statement_line_id UUID NOT NULL,
    cash_transaction_id UUID NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operation VARCHAR(40) NOT NULL DEFAULT 'RECONCILIATION_CANDIDATE_IGNORE',
    idempotency_key UUID NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    restored_by_user_id UUID,
    restored_at TIMESTAMPTZ,
    restore_reason VARCHAR(500),
    restore_operation VARCHAR(40),
    restore_idempotency_key UUID,
    restore_payload_hash CHAR(64),
    CONSTRAINT uq_reconciliation_candidate_suppression_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_reconciliation_candidate_suppression_key UNIQUE (tenant_id, operation, idempotency_key),
    CONSTRAINT uq_reconciliation_candidate_restore_key UNIQUE (tenant_id, restore_operation, restore_idempotency_key),
    CONSTRAINT fk_candidate_suppression_line FOREIGN KEY (tenant_id, financial_account_id, statement_line_id)
        REFERENCES statement_line (tenant_id, financial_account_id, id),
    CONSTRAINT fk_candidate_suppression_cash FOREIGN KEY (tenant_id, financial_account_id, cash_transaction_id)
        REFERENCES cash_transaction (tenant_id, financial_account_id, id),
    CONSTRAINT fk_candidate_suppression_actor FOREIGN KEY (tenant_id, created_by_user_id)
        REFERENCES membership (tenant_id, user_id),
    CONSTRAINT fk_candidate_suppression_restore_actor FOREIGN KEY (tenant_id, restored_by_user_id)
        REFERENCES membership (tenant_id, user_id),
    CONSTRAINT ck_candidate_suppression_operation CHECK (operation = 'RECONCILIATION_CANDIDATE_IGNORE'),
    CONSTRAINT ck_candidate_suppression_reason CHECK (length(trim(reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_candidate_suppression_hash CHECK (length(payload_hash) = 64),
    CONSTRAINT ck_candidate_suppression_restore CHECK (
        (restored_at IS NULL AND restored_by_user_id IS NULL AND restore_reason IS NULL
            AND restore_operation IS NULL AND restore_idempotency_key IS NULL AND restore_payload_hash IS NULL)
        OR (restored_at IS NOT NULL AND restored_by_user_id IS NOT NULL AND restore_reason IS NOT NULL
            AND length(trim(restore_reason)) > 0 AND restore_operation = 'RECONCILIATION_CANDIDATE_RESTORE'
            AND restore_idempotency_key IS NOT NULL AND restore_payload_hash IS NOT NULL AND length(restore_payload_hash) = 64)
    )
);

CREATE UNIQUE INDEX uq_reconciliation_candidate_suppression_active_pair
    ON reconciliation_candidate_suppression (tenant_id, statement_line_id, cash_transaction_id)
    WHERE restored_at IS NULL;
CREATE INDEX idx_reconciliation_candidate_suppression_active_line
    ON reconciliation_candidate_suppression (tenant_id, statement_line_id, created_at DESC)
    WHERE restored_at IS NULL;
