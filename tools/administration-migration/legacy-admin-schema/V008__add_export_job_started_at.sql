ALTER TABLE export_job
    ADD COLUMN started_at DATETIME(6) NULL AFTER processed_count;

UPDATE export_job
SET status = 'PENDING'
WHERE status = 'PROCESSING';
