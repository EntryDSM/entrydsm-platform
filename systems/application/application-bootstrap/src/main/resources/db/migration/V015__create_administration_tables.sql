-- 관리자 업무 소유권 이전 준비. 데이터 복사는 점검 시간에 별도 검증 후 수행한다.
-- 기존 원서와 정책 값을 추정하여 채우지 않는다.
CREATE TABLE score_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    policy_version INT NOT NULL,
    subject_weight DOUBLE NOT NULL,
    attendance_weight DOUBLE NOT NULL,
    volunteer_weight DOUBLE NOT NULL,
    rounding_scale INT NOT NULL,
    effective_from DATETIME(6) NOT NULL,
    updated_by VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_score_policy_version (policy_version)
);

CREATE TABLE export_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    export_job_id VARCHAR(40) NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    object_key VARCHAR(255) NULL,
    total_count INT NOT NULL DEFAULT 0,
    processed_count INT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NULL,
    failure_code VARCHAR(80) NULL,
    failure_message VARCHAR(255) NULL,
    failed_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_export_job_id (export_job_id)
);

CREATE TABLE screening (
    applicant_id    BIGINT      NOT NULL,
    examinee_number VARCHAR(20) NULL,
    is_arrived      BIT(1)      NOT NULL,
    status          VARCHAR(20) NOT NULL,
    arrived_at      DATETIME(6) NULL,
    updated_at      DATETIME(6) NULL,
    PRIMARY KEY (applicant_id),
    KEY idx_screening_status (status)
);

CREATE TABLE admission_quota (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admission_type VARCHAR(20) NOT NULL,
    quota INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admission_quota_type (admission_type)
);
