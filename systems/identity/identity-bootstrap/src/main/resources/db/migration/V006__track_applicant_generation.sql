ALTER TABLE application_projections
    ADD COLUMN applicant_id BIGINT NULL;

ALTER TABLE student_profiles
    ADD COLUMN last_deleted_applicant_id BIGINT NULL;
