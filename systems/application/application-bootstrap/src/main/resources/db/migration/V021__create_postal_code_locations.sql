CREATE TABLE postal_code_locations (
    zip_code CHAR(5) NOT NULL,
    representative_address VARCHAR(255) NOT NULL,
    latitude DECIMAL(17,14) NOT NULL,
    longitude DECIMAL(18,14) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (zip_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 여러 인스턴스가 빈 테이블을 동시에 초기화하지 않도록 잠금 행을 유지한다.
CREATE TABLE postal_code_location_seed_lock (
    id TINYINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO postal_code_location_seed_lock (id) VALUES (1);
