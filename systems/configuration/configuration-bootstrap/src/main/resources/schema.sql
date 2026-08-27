-- configuration_db 스키마.
-- ddl-auto 가 validate 이고 마이그레이션 도구가 없으므로 배포 전에 직접 적용한다.

CREATE TABLE IF NOT EXISTS environment_variable (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    env_key     VARCHAR(255) NOT NULL,
    env_value   TEXT         NOT NULL,
    description TEXT         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_environment_variable_env_key (env_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS files (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    original_name VARCHAR(255) NOT NULL,
    object_key    VARCHAR(255) NOT NULL,
    bucket        VARCHAR(100) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    checksum      VARCHAR(64)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_files_object_key (object_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
