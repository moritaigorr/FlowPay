CREATE TABLE financial_account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(120) NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    currency CHAR(3) NOT NULL DEFAULT 'BRL',
    opening_balance_date DATE NOT NULL,
    creation_key UUID NOT NULL,
    creation_payload_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_financial_account_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_financial_account_create_key UNIQUE (tenant_id, creation_key),
    CONSTRAINT ck_financial_account_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_financial_account_type CHECK (account_type IN ('BANK', 'CASH', 'OTHER')),
    CONSTRAINT ck_financial_account_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_financial_account_currency CHECK (currency = 'BRL'),
    CONSTRAINT ck_financial_account_hash CHECK (length(creation_payload_hash) = 64)
);

CREATE INDEX idx_financial_account_tenant_status_name
    ON financial_account (tenant_id, status, lower(name), id);

CREATE TABLE cash_transaction (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    financial_account_id UUID NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'BRL',
    effective_date DATE NOT NULL,
    origin VARCHAR(32) NOT NULL,
    description VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cash_transaction_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cash_transaction_account FOREIGN KEY (tenant_id, financial_account_id)
        REFERENCES financial_account (tenant_id, id),
    CONSTRAINT ck_cash_transaction_direction CHECK (direction IN ('INFLOW', 'OUTFLOW')),
    CONSTRAINT ck_cash_transaction_amount CHECK (amount_minor > 0 AND amount_minor <= 9000000000000000),
    CONSTRAINT ck_cash_transaction_currency CHECK (currency = 'BRL'),
    CONSTRAINT ck_cash_transaction_origin CHECK (origin IN ('OPENING_BALANCE'))
);

CREATE UNIQUE INDEX uq_cash_transaction_opening_balance
    ON cash_transaction (tenant_id, financial_account_id)
    WHERE origin = 'OPENING_BALANCE';

CREATE INDEX idx_cash_transaction_account_date
    ON cash_transaction (tenant_id, financial_account_id, effective_date, id);
