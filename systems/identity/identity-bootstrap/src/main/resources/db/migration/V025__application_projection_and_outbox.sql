CREATE TABLE application_projections (
    user_id BIGINT NOT NULL,
    applicant_status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMP(6) NULL,
    pass_status VARCHAR(20) NOT NULL,
    announced_at TIMESTAMP(6) NULL,
    state_updated_at TIMESTAMP(6) NOT NULL,
    source_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id)
);

CREATE TABLE identity_outbox (
    event_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    source_version BIGINT NOT NULL,
    applicant_status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMP(6) NULL,
    pass_status VARCHAR(20) NOT NULL,
    announced_at TIMESTAMP(6) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    reason VARCHAR(255) NULL,
    published_at TIMESTAMP(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (event_id),
    INDEX idx_identity_outbox_pending (published_at, created_at)
);
