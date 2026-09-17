CREATE TABLE institution_codes (
    code VARCHAR(7) NOT NULL,
    full_name VARCHAR(50) NOT NULL,
    name VARCHAR(34) NOT NULL,
    representative_code VARCHAR(7) NOT NULL,
    type VARCHAR(15) NOT NULL,
    status VARCHAR(2) NOT NULL,
    registrant VARCHAR(3) NULL,
    PRIMARY KEY (code)
);
