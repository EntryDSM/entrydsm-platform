CREATE TABLE applicant_export_event (
    event_id VARCHAR(36) PRIMARY KEY,
    applicant_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    applicant_status VARCHAR(30) NOT NULL,
    event_version BIGINT NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    KEY ix_applicant_export_event_processed (processed)
);

CREATE TABLE applicant_export_projection (
    applicant_id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    payload LONGBLOB NOT NULL,
    UNIQUE KEY uk_applicant_export_projection_account (account_id)
);
