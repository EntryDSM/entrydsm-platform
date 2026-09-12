-- admin_db 스키마.
-- ddl-auto 가 validate 이고 마이그레이션 도구가 없으므로 배포 전에 직접 적용한다.
-- configuration 시스템과 같은 방식이다.

-- 원서 본문(이름·지역·전형·학적)은 application 시스템이 소유한다. admin 은 gRPC 로
-- 조회하고, 전형을 진행하며 직접 매기는 값만 여기에 둔다.
-- 식별자는 application 의 지원자 식별자를 그대로 쓴다.
CREATE TABLE IF NOT EXISTS applicant_screening (
    applicant_id      BIGINT      NOT NULL,
    receipt_number    INT         NOT NULL,
    examinee_number   VARCHAR(20) NULL,
    document_received BIT(1)      NOT NULL DEFAULT b'0',
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    subject_score     DOUBLE      NULL,
    attendance_score  DOUBLE      NULL,
    volunteer_score   DOUBLE      NULL,
    total_score       DOUBLE      NULL,
    updated_at        DATETIME(6) NULL,
    PRIMARY KEY (applicant_id),
    UNIQUE KEY uk_applicant_screening_receipt_number (receipt_number),
    KEY idx_applicant_screening_status (status)
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

-- 모집 지역 × 전형 조합별 정원. PUT /admin/admission-quotas 가 전체를 교체한다.
CREATE TABLE IF NOT EXISTS admission_quota (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    region         VARCHAR(20) NOT NULL,
    admission_type VARCHAR(20) NOT NULL,
    quota          INT         NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    updated_by     VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admission_quota_region_type (region, admission_type)
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

CREATE TABLE IF NOT EXISTS question_answer (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    question_id BIGINT      NOT NULL,
    content     TEXT        NOT NULL,
    answered_by VARCHAR(50) NOT NULL,
    answered_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_question_answer_question_id (question_id)
);
