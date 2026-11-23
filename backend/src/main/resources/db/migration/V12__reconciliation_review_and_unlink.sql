ALTER TABLE statement_line
    ADD COLUMN review_status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN review_reason VARCHAR(500),
    ADD COLUMN review_updated_by_user_id UUID,
    ADD COLUMN review_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT fk_statement_line_review_actor FOREIGN KEY (tenant_id, review_updated_by_user_id)
        REFERENCES membership (tenant_id, user_id),
    ADD CONSTRAINT ck_statement_line_review_status CHECK (review_status IN ('PENDING', 'REVIEWED', 'IGNORED')),
    ADD CONSTRAINT ck_statement_line_review_state CHECK (
        (review_status = 'PENDING' AND review_reason IS NULL AND review_updated_by_user_id IS NULL AND review_updated_at IS NULL)
        OR (review_status IN ('REVIEWED', 'IGNORED') AND review_reason IS NOT NULL
            AND length(trim(review_reason)) > 0 AND review_updated_by_user_id IS NOT NULL AND review_updated_at IS NOT NULL)
    );

CREATE TABLE statement_line_review_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    financial_account_id UUID NOT NULL,
    statement_line_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operation VARCHAR(32) NOT NULL DEFAULT 'STATEMENT_LINE_REVIEW',
    idempotency_key UUID NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    actor_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_statement_line_review_event_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_statement_line_review_event_key UNIQUE (tenant_id, operation, idempotency_key),
    CONSTRAINT fk_statement_line_review_event_line FOREIGN KEY (tenant_id, financial_account_id, statement_line_id)
        REFERENCES statement_line (tenant_id, financial_account_id, id),
    CONSTRAINT fk_statement_line_review_event_actor FOREIGN KEY (tenant_id, actor_user_id)
        REFERENCES membership (tenant_id, user_id),
    CONSTRAINT ck_statement_line_review_event_action CHECK (action IN ('MARK_REVIEWED', 'IGNORE_LINE', 'REOPEN')),
    CONSTRAINT ck_statement_line_review_event_operation CHECK (operation = 'STATEMENT_LINE_REVIEW'),
    CONSTRAINT ck_statement_line_review_event_reason CHECK (length(trim(reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_statement_line_review_event_hash CHECK (length(payload_hash) = 64)
);

CREATE INDEX idx_statement_line_review_history
    ON statement_line_review_event (tenant_id, statement_line_id, created_at DESC, id);

ALTER TABLE reconciliation_match
    ADD COLUMN reversal_operation VARCHAR(40),
    ADD COLUMN reversal_idempotency_key UUID,
    ADD COLUMN reversal_payload_hash CHAR(64),
    ADD CONSTRAINT uq_reconciliation_match_reversal_key UNIQUE (tenant_id, reversal_operation, reversal_idempotency_key),
    ADD CONSTRAINT ck_reconciliation_match_reversal_idempotency CHECK (
        (reversed_at IS NULL AND reversal_operation IS NULL AND reversal_idempotency_key IS NULL AND reversal_payload_hash IS NULL)
        OR (reversed_at IS NOT NULL AND reversal_operation = 'RECONCILIATION_UNLINK'
            AND reversal_idempotency_key IS NOT NULL AND reversal_payload_hash IS NOT NULL AND length(reversal_payload_hash) = 64)
    );
