CREATE TABLE company (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    demo BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ,
    CONSTRAINT company_expiry CHECK ((demo AND expires_at IS NOT NULL) OR (NOT demo AND expires_at IS NULL))
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    demo BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, company_id),
    CONSTRAINT user_password CHECK ((demo AND password_hash IS NULL) OR (NOT demo AND password_hash IS NOT NULL))
);

CREATE TABLE customer (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
    email VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, company_id)
);
CREATE INDEX customer_company_created_idx ON customer(company_id, created_at DESC, id DESC);

CREATE TABLE payment (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    customer_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0 AND amount <= 99999999999999999.99),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'BRL'),
    status VARCHAR(9) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    description VARCHAR(500),
    simulation_scenario VARCHAR(7) NOT NULL CHECK (simulation_scenario IN ('APPROVE','DECLINE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, company_id),
    FOREIGN KEY (customer_id, company_id) REFERENCES customer(id, company_id)
);
CREATE INDEX payment_company_created_idx ON payment(company_id, created_at DESC, id DESC);
CREATE INDEX payment_company_status_created_idx ON payment(company_id, status, created_at DESC, id DESC);
CREATE INDEX payment_company_customer_created_idx ON payment(company_id, customer_id, created_at DESC, id DESC);

CREATE TABLE payment_event (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    payment_id UUID NOT NULL,
    sequence BIGINT NOT NULL CHECK (sequence > 0),
    previous_status VARCHAR(9) CHECK (previous_status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    new_status VARCHAR(9) NOT NULL CHECK (new_status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    reason_code VARCHAR(32) CHECK (reason_code = 'SIMULATED_DECLINE'),
    actor_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (payment_id, sequence),
    FOREIGN KEY (payment_id, company_id) REFERENCES payment(id, company_id),
    FOREIGN KEY (actor_user_id, company_id) REFERENCES app_user(id, company_id)
);

-- Nullable result fields allow an in-transaction reservation before payment creation.
-- The creation service must complete all result fields before committing (R08).
CREATE TABLE idempotency_record (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    operation VARCHAR(32) NOT NULL CHECK (operation = 'create-payment'),
    key VARCHAR(128) COLLATE "C" NOT NULL CHECK (key ~ '^[!-~]{1,128}$'),
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    payment_id UUID,
    http_status INTEGER,
    response_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (company_id, operation, key),
    FOREIGN KEY (payment_id, company_id) REFERENCES payment(id, company_id),
    CONSTRAINT idempotency_result CHECK (
        (payment_id IS NULL AND http_status IS NULL AND response_json IS NULL)
        OR (payment_id IS NOT NULL AND http_status IS NOT NULL AND http_status = 201 AND response_json IS NOT NULL)
    )
);
