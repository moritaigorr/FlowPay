CREATE TABLE contact (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(160) NOT NULL,
    is_customer BOOLEAN NOT NULL DEFAULT FALSE,
    is_supplier BOOLEAN NOT NULL DEFAULT FALSE,
    email VARCHAR(254),
    phone VARCHAR(32),
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_contact_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_contact_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_contact_has_role CHECK (is_customer OR is_supplier)
);

CREATE INDEX idx_contact_tenant_name ON contact (tenant_id, lower(name), id);
