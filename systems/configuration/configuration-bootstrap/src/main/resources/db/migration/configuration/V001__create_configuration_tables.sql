CREATE TABLE environment_variable (
    id BIGINT NOT NULL AUTO_INCREMENT,
    env_key VARCHAR(255) NOT NULL,
    env_value TEXT NOT NULL,
    description TEXT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_environment_variable_env_key (env_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    original_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_files_object_key (object_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE schedule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_title (title),
    CONSTRAINT chk_schedule_period CHECK (start_at <= end_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
