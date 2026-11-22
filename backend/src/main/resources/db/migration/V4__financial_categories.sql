CREATE TABLE financial_category (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_financial_category_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_financial_category_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_financial_category_direction CHECK (direction IN ('INFLOW', 'OUTFLOW')),
    CONSTRAINT ck_financial_category_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uq_financial_category_tenant_direction_name
    ON financial_category (tenant_id, direction, lower(name));

CREATE INDEX idx_financial_category_tenant_status_direction
    ON financial_category (tenant_id, status, direction, lower(name), id);
