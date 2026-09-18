DROP TABLE IF EXISTS institution_codes;

CREATE TABLE institution_codes (
    code VARCHAR(20) NOT NULL COMMENT '기관코드',
    full_name VARCHAR(255) NOT NULL COMMENT '전체기관명',
    name VARCHAR(255) NOT NULL COMMENT '최하위기관명',
    postal_code VARCHAR(10) NULL COMMENT '우편번호',
    address VARCHAR(500) NULL COMMENT '학교도로명 주소',
    phone_number VARCHAR(30) NULL COMMENT '전화번호',
    fax_number VARCHAR(30) NULL COMMENT '팩스번호',

    PRIMARY KEY (code),
    INDEX idx_institution_name (name)
);

CREATE INDEX idx_institution_codes_name
    ON institution_codes (name);