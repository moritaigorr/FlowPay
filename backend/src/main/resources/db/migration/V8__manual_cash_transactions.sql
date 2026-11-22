ALTER TABLE cash_transaction DROP CONSTRAINT ck_cash_transaction_origin;
ALTER TABLE cash_transaction ADD CONSTRAINT ck_cash_transaction_origin
    CHECK (origin IN ('OPENING_BALANCE', 'TITLE_SETTLEMENT', 'SETTLEMENT_REVERSAL', 'MANUAL'));

ALTER TABLE cash_transaction
    ADD COLUMN category_id UUID,
    ADD COLUMN category_direction VARCHAR(8),
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN payload_hash CHAR(64),
    ADD CONSTRAINT fk_cash_transaction_category FOREIGN KEY (tenant_id, category_id, category_direction)
        REFERENCES financial_category (tenant_id, id, direction),
    ADD CONSTRAINT ck_cash_transaction_category_pair CHECK (
        (category_id IS NULL AND category_direction IS NULL)
        OR (category_id IS NOT NULL AND category_direction IN ('INFLOW', 'OUTFLOW'))
    ),
    ADD CONSTRAINT ck_cash_transaction_manual_idempotency CHECK (
        (origin = 'MANUAL' AND idempotency_key IS NOT NULL AND payload_hash IS NOT NULL AND category_id IS NOT NULL)
        OR (origin <> 'MANUAL' AND idempotency_key IS NULL AND payload_hash IS NULL)
    ),
    ADD CONSTRAINT ck_cash_transaction_payload_hash CHECK (payload_hash IS NULL OR length(payload_hash) = 64);

CREATE UNIQUE INDEX uq_cash_transaction_manual_key
    ON cash_transaction (tenant_id, origin, idempotency_key)
    WHERE origin = 'MANUAL';

UPDATE cash_transaction ct
SET category_id = t.category_id, category_direction = t.category_direction
FROM financial_settlement s
JOIN financial_title t ON t.tenant_id = s.tenant_id AND t.id = s.financial_title_id
WHERE ct.tenant_id = s.tenant_id AND ct.id = s.cash_transaction_id;

UPDATE cash_transaction ct
SET category_id = t.category_id, category_direction = t.category_direction
FROM financial_settlement_reversal r
JOIN financial_title t ON t.tenant_id = r.tenant_id AND t.id = r.financial_title_id
WHERE ct.tenant_id = r.tenant_id AND ct.id = r.cash_transaction_id;

CREATE INDEX idx_cash_transaction_tenant_date
    ON cash_transaction (tenant_id, effective_date DESC, created_at DESC, id);
