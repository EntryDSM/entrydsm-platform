-- V001 이 admission_quota 를 빠뜨려 빈 DB 에서는 ddl-auto=validate 가 실패했다.
-- 이미 손으로 만들어 둔 환경이 있어 IF NOT EXISTS 로 둔다.
CREATE TABLE IF NOT EXISTS admission_quota (
    id BIGINT NOT NULL AUTO_INCREMENT,
    region VARCHAR(20) NOT NULL,
    admission_type VARCHAR(20) NOT NULL,
    quota INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admission_quota_region_type (region, admission_type)
);
