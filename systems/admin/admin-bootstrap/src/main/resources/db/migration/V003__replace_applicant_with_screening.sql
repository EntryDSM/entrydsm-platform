-- 원서 본문의 소유자는 application 이다. admin 은 gRPC 로 조회하고,
-- 접수 번호·수험 번호·원서 도착 여부·전형 상태·성적만 직접 들고 간다.
-- 식별자는 application 의 지원자 식별자를 그대로 쓴다.
CREATE TABLE applicant_screening (
    applicant_id BIGINT NOT NULL,
    receipt_number INT NOT NULL,
    examinee_number VARCHAR(20) NULL,
    document_received BIT(1) NOT NULL DEFAULT b'0',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    subject_score DOUBLE NULL,
    attendance_score DOUBLE NULL,
    volunteer_score DOUBLE NULL,
    total_score DOUBLE NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (applicant_id),
    UNIQUE KEY uk_applicant_screening_receipt_number (receipt_number),
    KEY idx_applicant_screening_status (status)
);

-- 기존 applicant 행은 application 시스템이 없던 시절의 테스트 데이터다.
-- 실제 원서와 식별자를 맞출 방법이 없어 이관하지 않는다. (#139 의 notice 와 같은 판단)
DROP TABLE IF EXISTS applicant;
