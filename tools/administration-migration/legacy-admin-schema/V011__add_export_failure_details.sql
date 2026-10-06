ALTER TABLE export_job
    ADD COLUMN failure_code VARCHAR(80) NULL,
    ADD COLUMN failure_message VARCHAR(255) NULL,
    ADD COLUMN failed_count INT NOT NULL DEFAULT 0;
