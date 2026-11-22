CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tenant_name_nonblank CHECK (length(trim(name)) > 0)
);

CREATE TABLE membership (
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    role VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT ck_membership_role CHECK (role IN ('OWNER', 'MANAGER', 'READER'))
);
