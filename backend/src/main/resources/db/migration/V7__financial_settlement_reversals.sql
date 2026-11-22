ALTER TABLE cash_transaction DROP CONSTRAINT ck_cash_transaction_origin;
ALTER TABLE cash_transaction ADD CONSTRAINT ck_cash_transaction_origin
    CHECK (origin IN ('OPENING_BALANCE', 'TITLE_SETTLEMENT', 'SETTLEMENT_REVERSAL'));

ALTER TABLE financial_settlement
    ADD CONSTRAINT uq_financial_settlement_title_id UNIQUE (tenant_id, financial_title_id, id);

CREATE TABLE financial_settlement_reversal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    financial_title_id UUID NOT NULL,
    financial_settlement_id UUID NOT NULL,
    cash_transaction_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    effective_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operation VARCHAR(32) NOT NULL DEFAULT 'TITLE_SETTLEMENT_REVERSAL',
    idempotency_key UUID NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_financial_settlement_reversal_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_financial_settlement_reversal_key UNIQUE (tenant_id, operation, idempotency_key),
    CONSTRAINT uq_financial_settlement_reversal_cash UNIQUE (tenant_id, cash_transaction_id),
    CONSTRAINT fk_settlement_reversal_settlement FOREIGN KEY (tenant_id, financial_title_id, financial_settlement_id)
        REFERENCES financial_settlement (tenant_id, financial_title_id, id),
    CONSTRAINT fk_settlement_reversal_cash FOREIGN KEY (tenant_id, cash_transaction_id)
        REFERENCES cash_transaction (tenant_id, id),
    CONSTRAINT ck_settlement_reversal_amount CHECK (amount_minor > 0 AND amount_minor <= 9000000000000000),
    CONSTRAINT ck_settlement_reversal_reason CHECK (length(trim(reason)) > 0),
    CONSTRAINT ck_settlement_reversal_operation CHECK (operation = 'TITLE_SETTLEMENT_REVERSAL'),
    CONSTRAINT ck_settlement_reversal_hash CHECK (length(payload_hash) = 64)
);

CREATE INDEX idx_settlement_reversal_source ON financial_settlement_reversal (tenant_id, financial_settlement_id);
