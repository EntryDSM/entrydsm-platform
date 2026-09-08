-- configuration_db
-- ddl-auto: validate 이므로 애플리케이션 기동 전에 적용되어 있어야 한다.

CREATE TABLE IF NOT EXISTS schedule
(
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    title    VARCHAR(100) NOT NULL,
    start_at DATETIME(6)  NOT NULL,
    end_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_title (title),
    CONSTRAINT chk_schedule_period CHECK (start_at <= end_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
