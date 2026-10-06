CREATE TABLE screening_result_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    INDEX idx_screening_result_unpublished (published_at, id)
);

-- 이미 산출한 결과도 학생 서비스에 전달한다.
INSERT INTO screening_result_outbox (applicant_id, status, created_at)
SELECT applicant_id, status, COALESCE(updated_at, UTC_TIMESTAMP(6))
FROM screening WHERE status <> 'PENDING';
