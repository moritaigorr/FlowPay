ALTER TABLE cash_transaction DROP CONSTRAINT ck_cash_transaction_origin;
ALTER TABLE cash_transaction ADD CONSTRAINT ck_cash_transaction_origin
    CHECK (origin IN ('OPENING_BALANCE', 'TITLE_SETTLEMENT', 'SETTLEMENT_REVERSAL', 'MANUAL', 'STATEMENT_RECONCILIATION'));

ALTER TABLE cash_transaction
    ADD CONSTRAINT uq_cash_transaction_tenant_account_id UNIQUE (tenant_id, financial_account_id, id);

ALTER TABLE statement_import
    ADD CONSTRAINT uq_statement_import_tenant_account_id UNIQUE (tenant_id, financial_account_id, id);

ALTER TABLE statement_line ADD COLUMN financial_account_id UUID;
UPDATE statement_line sl
SET financial_account_id = si.financial_account_id
FROM statement_import si
WHERE si.tenant_id = sl.tenant_id AND si.id = sl.statement_import_id;
ALTER TABLE statement_line ALTER COLUMN financial_account_id SET NOT NULL;
ALTER TABLE statement_line
    ADD CONSTRAINT uq_statement_line_tenant_account_id UNIQUE (tenant_id, financial_account_id, id),
    ADD CONSTRAINT fk_statement_line_account_import FOREIGN KEY (tenant_id, financial_account_id, statement_import_id)
        REFERENCES statement_import (tenant_id, financial_account_id, id);

CREATE TABLE reconciliation_match (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    financial_account_id UUID NOT NULL,
    statement_line_id UUID NOT NULL,
    cash_transaction_id UUID NOT NULL,
    decision VARCHAR(20) NOT NULL,
    operation VARCHAR(40) NOT NULL DEFAULT 'STATEMENT_RECONCILIATION',
    idempotency_key UUID NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_by_user_id UUID,
    reversed_at TIMESTAMPTZ,
    reversal_reason VARCHAR(500),
    CONSTRAINT uq_reconciliation_match_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_reconciliation_match_operation_key UNIQUE (tenant_id, operation, idempotency_key),
    CONSTRAINT fk_reconciliation_match_line FOREIGN KEY (tenant_id, financial_account_id, statement_line_id)
        REFERENCES statement_line (tenant_id, financial_account_id, id),
    CONSTRAINT fk_reconciliation_match_cash FOREIGN KEY (tenant_id, financial_account_id, cash_transaction_id)
        REFERENCES cash_transaction (tenant_id, financial_account_id, id),
    CONSTRAINT fk_reconciliation_match_actor FOREIGN KEY (tenant_id, created_by_user_id)
        REFERENCES membership (tenant_id, user_id),
    CONSTRAINT fk_reconciliation_match_reversal_actor FOREIGN KEY (tenant_id, reversed_by_user_id)
        REFERENCES membership (tenant_id, user_id),
    CONSTRAINT ck_reconciliation_match_decision CHECK (decision IN ('LINK_EXISTING', 'CREATE_MOVEMENT')),
    CONSTRAINT ck_reconciliation_match_operation CHECK (operation = 'STATEMENT_RECONCILIATION'),
    CONSTRAINT ck_reconciliation_match_hash CHECK (length(payload_hash) = 64),
    CONSTRAINT ck_reconciliation_match_reversal CHECK (
        (reversed_at IS NULL AND reversed_by_user_id IS NULL AND reversal_reason IS NULL)
        OR (reversed_at IS NOT NULL AND reversed_by_user_id IS NOT NULL AND reversal_reason IS NOT NULL AND length(trim(reversal_reason)) > 0)
    )
);

CREATE UNIQUE INDEX uq_reconciliation_match_active_line
    ON reconciliation_match (tenant_id, statement_line_id) WHERE reversed_at IS NULL;
CREATE UNIQUE INDEX uq_reconciliation_match_active_cash
    ON reconciliation_match (tenant_id, cash_transaction_id) WHERE reversed_at IS NULL;
CREATE INDEX idx_reconciliation_match_account_created
    ON reconciliation_match (tenant_id, financial_account_id, created_at DESC, id);
