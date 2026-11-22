ALTER TABLE financial_category
    ADD CONSTRAINT uq_financial_category_tenant_id_direction UNIQUE (tenant_id, id, direction);

CREATE TABLE financial_title (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    kind VARCHAR(12) NOT NULL,
    description VARCHAR(180) NOT NULL,
    contact_id UUID NOT NULL,
    category_id UUID NOT NULL,
    category_direction VARCHAR(8) NOT NULL,
    planned_account_id UUID,
    original_amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'BRL',
    due_date DATE NOT NULL,
    notes VARCHAR(1000),
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    cancellation_reason VARCHAR(500),
    cancelled_at TIMESTAMPTZ,
    creation_key UUID NOT NULL,
    creation_payload_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_financial_title_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_financial_title_create_key UNIQUE (tenant_id, kind, creation_key),
    CONSTRAINT fk_financial_title_contact FOREIGN KEY (tenant_id, contact_id)
        REFERENCES contact (tenant_id, id),
    CONSTRAINT fk_financial_title_category_direction FOREIGN KEY (tenant_id, category_id, category_direction)
        REFERENCES financial_category (tenant_id, id, direction),
    CONSTRAINT fk_financial_title_account FOREIGN KEY (tenant_id, planned_account_id)
        REFERENCES financial_account (tenant_id, id),
    CONSTRAINT ck_financial_title_kind CHECK (kind IN ('RECEIVABLE', 'PAYABLE')),
    CONSTRAINT ck_financial_title_category_direction CHECK (
        (kind = 'RECEIVABLE' AND category_direction = 'INFLOW')
        OR (kind = 'PAYABLE' AND category_direction = 'OUTFLOW')
    ),
    CONSTRAINT ck_financial_title_description CHECK (length(trim(description)) > 0),
    CONSTRAINT ck_financial_title_amount CHECK (original_amount_minor > 0 AND original_amount_minor <= 9000000000000000),
    CONSTRAINT ck_financial_title_currency CHECK (currency = 'BRL'),
    CONSTRAINT ck_financial_title_status CHECK (status IN ('OPEN', 'CANCELLED')),
    CONSTRAINT ck_financial_title_cancellation CHECK (
        (status = 'OPEN' AND cancelled_at IS NULL AND cancellation_reason IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND length(trim(cancellation_reason)) > 0)
    ),
    CONSTRAINT ck_financial_title_hash CHECK (length(creation_payload_hash) = 64)
);

CREATE INDEX idx_financial_title_tenant_kind_due
    ON financial_title (tenant_id, kind, due_date, id);

CREATE INDEX idx_financial_title_tenant_contact
    ON financial_title (tenant_id, contact_id, kind, due_date);

CREATE INDEX idx_financial_title_tenant_category
    ON financial_title (tenant_id, category_id, kind, due_date);
