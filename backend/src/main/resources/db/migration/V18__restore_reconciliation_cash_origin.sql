ALTER TABLE cash_transaction
    DROP CONSTRAINT ck_cash_transaction_origin,
    ADD CONSTRAINT ck_cash_transaction_origin CHECK (
        origin IN (
            'OPENING_BALANCE',
            'TITLE_SETTLEMENT',
            'SETTLEMENT_REVERSAL',
            'MANUAL',
            'TRANSFER',
            'ADJUSTMENT',
            'STATEMENT_RECONCILIATION'
        )
    );
