CREATE TABLE cash_transfer (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    source_account_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    effective_date DATE NOT NULL,
    description VARCHAR(160) NOT NULL,
    idempotency_key UUID NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cash_transfer PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_cash_transfer_fact UNIQUE (tenant_id, id, amount_minor, effective_date, description),
    CONSTRAINT uq_cash_transfer_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_cash_transfer_source FOREIGN KEY (tenant_id, source_account_id)
        REFERENCES financial_account (tenant_id, id),
    CONSTRAINT fk_cash_transfer_destination FOREIGN KEY (tenant_id, destination_account_id)
        REFERENCES financial_account (tenant_id, id),
    CONSTRAINT ck_cash_transfer_distinct_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT ck_cash_transfer_amount CHECK (amount_minor > 0 AND amount_minor <= 9000000000000000),
    CONSTRAINT ck_cash_transfer_description CHECK (length(trim(description)) > 0),
    CONSTRAINT ck_cash_transfer_hash CHECK (length(payload_hash) = 64)
);

ALTER TABLE cash_transaction
    ADD COLUMN cash_transfer_id UUID,
    DROP CONSTRAINT ck_cash_transaction_origin,
    ADD CONSTRAINT ck_cash_transaction_origin CHECK (
        origin IN ('OPENING_BALANCE', 'TITLE_SETTLEMENT', 'SETTLEMENT_REVERSAL', 'MANUAL', 'TRANSFER')
    ),
    ADD CONSTRAINT fk_cash_transaction_transfer_fact FOREIGN KEY
        (tenant_id, cash_transfer_id, amount_minor, effective_date, description)
        REFERENCES cash_transfer (tenant_id, id, amount_minor, effective_date, description),
    ADD CONSTRAINT ck_cash_transaction_transfer_pair CHECK (
        (origin = 'TRANSFER' AND cash_transfer_id IS NOT NULL AND category_id IS NULL AND category_direction IS NULL
            AND idempotency_key IS NULL AND payload_hash IS NULL)
        OR (origin <> 'TRANSFER' AND cash_transfer_id IS NULL)
    );

CREATE UNIQUE INDEX uq_cash_transfer_movement_leg
    ON cash_transaction (tenant_id, cash_transfer_id, direction)
    WHERE cash_transfer_id IS NOT NULL;

CREATE INDEX idx_cash_transfer_tenant_date
    ON cash_transfer (tenant_id, effective_date DESC, created_at DESC, id);
