-- admin_db 스키마.
-- ddl-auto 가 validate 이고 마이그레이션 도구가 없으므로 배포 전에 직접 적용한다.
-- configuration 시스템과 같은 방식이다.

CREATE TABLE IF NOT EXISTS applicant (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    receipt_number    INT          NOT NULL,
    name              VARCHAR(50)  NOT NULL,
    birth_date        DATE         NOT NULL,
    phone_number      VARCHAR(20)  NOT NULL,
    region            VARCHAR(20)  NOT NULL,
    admission_type    VARCHAR(20)  NOT NULL,
    graduation_status VARCHAR(20)  NOT NULL,
    school_name       VARCHAR(100) NOT NULL,
    examinee_number   VARCHAR(20)  NULL,
    is_submitted      BIT(1)       NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    subject_score     DOUBLE       NULL,
    attendance_score  DOUBLE       NULL,
    volunteer_score   DOUBLE       NULL,
    total_score       DOUBLE       NULL,
    submitted_at      DATETIME(6)  NULL,
    updated_at        DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_applicant_receipt_number (receipt_number),
    KEY idx_applicant_status (status),
    KEY idx_applicant_total_score (total_score)
);

CREATE TABLE IF NOT EXISTS score_policy (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    policy_version    INT         NOT NULL,
    subject_weight    DOUBLE      NOT NULL,
    attendance_weight DOUBLE      NOT NULL,
    volunteer_weight  DOUBLE      NOT NULL,
    rounding_scale    INT         NOT NULL,
    effective_from    DATETIME(6) NOT NULL,
    updated_by        VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_score_policy_version (policy_version)
);

CREATE TABLE IF NOT EXISTS export_job (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    export_job_id VARCHAR(40) NOT NULL,
    type          VARCHAR(30) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    object_key    VARCHAR(255) NULL,
    created_at    DATETIME(6) NOT NULL,
    completed_at  DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_export_job_id (export_job_id)
);

CREATE TABLE IF NOT EXISTS notice (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    title          VARCHAR(200) NOT NULL,
    content        TEXT         NOT NULL,
    is_pinned      BIT(1)       NOT NULL,
    attachment_ids VARCHAR(500) NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS question_answer (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    question_id BIGINT      NOT NULL,
    content     TEXT        NOT NULL,
    answered_by VARCHAR(50) NOT NULL,
    answered_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_question_answer_question_id (question_id)
);
