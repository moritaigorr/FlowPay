ALTER TABLE cash_transaction
    DROP CONSTRAINT ck_cash_transaction_origin,
    DROP CONSTRAINT ck_cash_transaction_manual_idempotency,
    ADD COLUMN adjustment_reason VARCHAR(500),
    ADD COLUMN created_by_user_id UUID,
    ADD CONSTRAINT ck_cash_transaction_origin CHECK (
        origin IN ('OPENING_BALANCE', 'TITLE_SETTLEMENT', 'SETTLEMENT_REVERSAL', 'MANUAL', 'TRANSFER', 'ADJUSTMENT')
    ),
    ADD CONSTRAINT fk_cash_transaction_adjustment_actor FOREIGN KEY (tenant_id, created_by_user_id)
        REFERENCES membership (tenant_id, user_id),
    ADD CONSTRAINT ck_cash_transaction_operation_audit CHECK (
        (origin = 'MANUAL' AND idempotency_key IS NOT NULL AND payload_hash IS NOT NULL
            AND category_id IS NOT NULL AND adjustment_reason IS NULL AND created_by_user_id IS NULL)
        OR (origin = 'ADJUSTMENT' AND idempotency_key IS NOT NULL AND payload_hash IS NOT NULL
            AND category_id IS NULL AND category_direction IS NULL
            AND adjustment_reason IS NOT NULL AND length(trim(adjustment_reason)) > 0 AND created_by_user_id IS NOT NULL)
        OR (origin NOT IN ('MANUAL', 'ADJUSTMENT') AND idempotency_key IS NULL AND payload_hash IS NULL
            AND adjustment_reason IS NULL AND created_by_user_id IS NULL)
    );

CREATE UNIQUE INDEX uq_cash_transaction_adjustment_key
    ON cash_transaction (tenant_id, idempotency_key)
    WHERE origin = 'ADJUSTMENT';
