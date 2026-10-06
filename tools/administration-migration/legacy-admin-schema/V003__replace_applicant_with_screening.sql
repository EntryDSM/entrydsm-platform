-- 지원자 원서는 application 이 갖는다. admin 은 전형 진행만 남긴다.
-- applicant 테이블을 채우는 코드가 없어 운영 데이터는 0행이다. 배포 전에 확인한다.
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

DROP TABLE applicant;
