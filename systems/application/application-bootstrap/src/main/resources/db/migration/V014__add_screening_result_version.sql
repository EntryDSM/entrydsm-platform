ALTER TABLE applicants ADD COLUMN screening_result_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE applicants ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
