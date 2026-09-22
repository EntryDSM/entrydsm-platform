ALTER TABLE export_job
    ADD COLUMN total_count INT NOT NULL DEFAULT 0,
    ADD COLUMN processed_count INT NOT NULL DEFAULT 0;
