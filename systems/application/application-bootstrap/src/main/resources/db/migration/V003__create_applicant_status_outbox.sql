ALTER TABLE applicants ADD COLUMN status_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE applicant_status_outbox (
    event_id VARCHAR(36) NOT NULL,
    account_id BIGINT NOT NULL,
    payload LONGBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (event_id),
    KEY ix_applicant_status_outbox_unpublished (published_at, created_at)
);
