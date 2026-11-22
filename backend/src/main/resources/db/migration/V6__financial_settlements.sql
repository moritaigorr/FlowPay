ALTER TABLE cash_transaction DROP CONSTRAINT ck_cash_transaction_origin;
ALTER TABLE cash_transaction ADD CONSTRAINT ck_cash_transaction_origin
    CHECK (origin IN ('OPENING_BALANCE', 'TITLE_SETTLEMENT'));

CREATE TABLE financial_settlement (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    financial_title_id UUID NOT NULL,
    cash_transaction_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    effective_date DATE NOT NULL,
    operation VARCHAR(20) NOT NULL DEFAULT 'TITLE_SETTLEMENT',
    idempotency_key UUID NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_financial_settlement_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_financial_settlement_idempotency UNIQUE (tenant_id, operation, idempotency_key),
    CONSTRAINT uq_financial_settlement_cash UNIQUE (tenant_id, cash_transaction_id),
    CONSTRAINT fk_financial_settlement_title FOREIGN KEY (tenant_id, financial_title_id)
        REFERENCES financial_title (tenant_id, id),
    CONSTRAINT fk_financial_settlement_cash FOREIGN KEY (tenant_id, cash_transaction_id)
        REFERENCES cash_transaction (tenant_id, id),
    CONSTRAINT ck_financial_settlement_amount CHECK (amount_minor > 0 AND amount_minor <= 9000000000000000),
    CONSTRAINT ck_financial_settlement_operation CHECK (operation = 'TITLE_SETTLEMENT'),
    CONSTRAINT ck_financial_settlement_hash CHECK (length(payload_hash) = 64)
);

CREATE INDEX idx_financial_settlement_title ON financial_settlement (tenant_id, financial_title_id);
