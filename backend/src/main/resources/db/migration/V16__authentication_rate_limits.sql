CREATE TABLE authentication_rate_limit (
    bucket_hash CHAR(64) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    attempt_count SMALLINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_authentication_rate_limit_hash CHECK (bucket_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_authentication_rate_limit_attempts CHECK (attempt_count BETWEEN 1 AND 1001)
);

CREATE INDEX idx_authentication_rate_limit_updated_at
    ON authentication_rate_limit (updated_at);
