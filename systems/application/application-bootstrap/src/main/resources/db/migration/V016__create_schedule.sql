-- 일정은 별도 이전 도구로 ID와 시각을 검증하여 복사한다.
CREATE TABLE schedule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_title (title),
    CONSTRAINT chk_schedule_period CHECK (start_at <= end_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
