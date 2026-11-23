CREATE INDEX idx_cash_reconciliation_candidates
    ON cash_transaction (tenant_id, financial_account_id, direction, amount_minor, effective_date)
    WHERE origin <> 'OPENING_BALANCE';
