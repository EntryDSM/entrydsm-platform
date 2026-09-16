CREATE TABLE accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login_id_hash VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_accounts_login_id_hash (login_id_hash)
);

CREATE TABLE student_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    signup_type VARCHAR(10) NOT NULL,
    name_encrypted VARCHAR(255) NOT NULL,
    phone_encrypted VARCHAR(255) NOT NULL,
    birthdate VARCHAR(255) NOT NULL,
    submitted_at TIMESTAMP(6) NULL,
    applicant_status VARCHAR(20) NOT NULL,
    pass_status VARCHAR(20) NOT NULL,
    announced_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_profiles_account_id (account_id),
    CONSTRAINT fk_student_profiles_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
);
