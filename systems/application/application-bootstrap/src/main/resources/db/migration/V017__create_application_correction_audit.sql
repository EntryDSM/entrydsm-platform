-- 개인정보 전후 값은 저장하지 않는다. 사유는 애플리케이션에서 암호화한다.
-- 별도 보존 정책 확정 전까지 자동 삭제하지 않으며 원서 삭제 시에도 이력을 보존한다.
CREATE TABLE application_correction_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_id BIGINT NOT NULL,
    editor_id VARCHAR(128) NOT NULL,
    reason VARCHAR(4096) NOT NULL,
    changed_fields VARCHAR(1024) NOT NULL,
    version BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_application_correction_version (applicant_id, version),
    KEY idx_application_correction_occurred (occurred_at)
);
