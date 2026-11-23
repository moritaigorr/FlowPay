ALTER TABLE statement_import
    ADD COLUMN statement_opening_balance_minor BIGINT,
    ADD COLUMN statement_opening_balance_date DATE,
    ADD COLUMN statement_closing_balance_minor BIGINT,
    ADD COLUMN statement_closing_balance_date DATE,
    ADD CONSTRAINT ck_statement_import_balance_pair CHECK (
        (statement_opening_balance_minor IS NULL
            AND statement_opening_balance_date IS NULL
            AND statement_closing_balance_minor IS NULL
            AND statement_closing_balance_date IS NULL)
        OR
        (statement_opening_balance_minor IS NOT NULL
            AND statement_opening_balance_date IS NOT NULL
            AND statement_closing_balance_minor IS NOT NULL
            AND statement_closing_balance_date IS NOT NULL)
    ),
    ADD CONSTRAINT ck_statement_import_opening_balance_range CHECK (
        statement_opening_balance_minor IS NULL
        OR statement_opening_balance_minor BETWEEN -9000000000000000 AND 9000000000000000
    ),
    ADD CONSTRAINT ck_statement_import_closing_balance_range CHECK (
        statement_closing_balance_minor IS NULL
        OR statement_closing_balance_minor BETWEEN -9000000000000000 AND 9000000000000000
    ),
    ADD CONSTRAINT ck_statement_import_balance_dates CHECK (
        statement_opening_balance_date IS NULL
        OR statement_closing_balance_date > statement_opening_balance_date
    );
