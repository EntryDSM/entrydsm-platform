-- configuration_db
-- ddl-auto: validate 이므로 애플리케이션 기동 전에 적용되어 있어야 한다.
-- 마이그레이션 도구(Flyway 등) 도입 전까지 수기로 적용한다.

CREATE TABLE IF NOT EXISTS files
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    original_name VARCHAR(255) NOT NULL,
    object_key    VARCHAR(255) NOT NULL,
    bucket        VARCHAR(100) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    checksum      VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_files_object_key (object_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
