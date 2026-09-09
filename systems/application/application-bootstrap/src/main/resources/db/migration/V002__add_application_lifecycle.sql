ALTER TABLE applicants
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN submitted_at DATETIME(6) NULL,
    ADD COLUMN cancel_reason VARCHAR(500) NULL,
    ADD CONSTRAINT uk_applicants_account_id UNIQUE (account_id);
