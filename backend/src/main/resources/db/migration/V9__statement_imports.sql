CREATE TABLE statement_import (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    financial_account_id UUID NOT NULL,
    file_checksum CHAR(64) NOT NULL,
    file_content BYTEA NOT NULL,
    total_rows INTEGER NOT NULL,
    accepted_rows INTEGER NOT NULL,
    rejected_rows INTEGER NOT NULL,
    duplicate_rows INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_statement_import_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_statement_import_account_checksum UNIQUE (tenant_id, financial_account_id, file_checksum),
    CONSTRAINT fk_statement_import_account FOREIGN KEY (tenant_id, financial_account_id)
        REFERENCES financial_account (tenant_id, id),
    CONSTRAINT ck_statement_import_checksum CHECK (length(file_checksum) = 64),
    CONSTRAINT ck_statement_import_counts CHECK (
        total_rows >= 0 AND accepted_rows >= 0 AND rejected_rows >= 0 AND duplicate_rows >= 0
        AND total_rows = accepted_rows + rejected_rows + duplicate_rows
    )
);

CREATE INDEX idx_statement_import_account_created
    ON statement_import (tenant_id, financial_account_id, created_at DESC, id);

CREATE TABLE statement_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    statement_import_id UUID NOT NULL,
    row_number INTEGER NOT NULL,
    effective_date DATE,
    description TEXT NOT NULL,
    direction VARCHAR(8),
    amount_minor BIGINT,
    row_status VARCHAR(12) NOT NULL,
    validation_errors TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_statement_line_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_statement_line_import_row UNIQUE (tenant_id, statement_import_id, row_number),
    CONSTRAINT fk_statement_line_import FOREIGN KEY (tenant_id, statement_import_id)
        REFERENCES statement_import (tenant_id, id),
    CONSTRAINT ck_statement_line_row_number CHECK (row_number > 1),
    CONSTRAINT ck_statement_line_direction CHECK (direction IS NULL OR direction IN ('INFLOW', 'OUTFLOW')),
    CONSTRAINT ck_statement_line_amount CHECK (amount_minor IS NULL OR (amount_minor > 0 AND amount_minor <= 9000000000000000)),
    CONSTRAINT ck_statement_line_status CHECK (row_status IN ('ACCEPTED', 'REJECTED', 'DUPLICATE')),
    CONSTRAINT ck_statement_line_valid_data CHECK (
        row_status = 'REJECTED' OR (effective_date IS NOT NULL AND direction IS NOT NULL AND amount_minor IS NOT NULL)
    )
);

CREATE INDEX idx_statement_line_date ON statement_line (tenant_id, effective_date, statement_import_id, id);
