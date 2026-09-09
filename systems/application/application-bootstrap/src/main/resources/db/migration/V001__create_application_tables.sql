CREATE TABLE applicants (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    account_id             BIGINT       NOT NULL,
    photo_file_id          BIGINT       NULL,
    name                   VARCHAR(20)  NULL,
    phone_number           VARCHAR(16)  NULL,
    gender                 VARCHAR(10)  NULL,
    birthdate              DATE         NULL,
    special_admission_type VARCHAR(32)  NOT NULL,
    admission_type         VARCHAR(16)  NULL,
    region                 VARCHAR(16)  NULL,
    graduation_type        VARCHAR(16)  NULL,
    graduation_date        DATE         NULL,
    guardian_name          VARCHAR(20)  NULL,
    guardian_phone_number  VARCHAR(16)  NULL,
    guardian_gender        VARCHAR(10)  NULL,
    guardian_relation      VARCHAR(10)  NULL,
    address_base           VARCHAR(255) NULL,
    address_detail         VARCHAR(255) NULL,
    zip_code               VARCHAR(10)  NULL,
    introduction           TEXT         NULL,
    study_plan             TEXT         NULL,
    total_score            DOUBLE       NULL,
    total_score_updated_at DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_applicants_account_id (account_id)
);

CREATE TABLE middle_school_infos (
    applicant_id   BIGINT      NOT NULL,
    school_name    VARCHAR(50) NOT NULL,
    student_number VARCHAR(8)  NOT NULL,
    school_phone   VARCHAR(16) NOT NULL,
    teacher_name   VARCHAR(20) NOT NULL,
    PRIMARY KEY (applicant_id),
    CONSTRAINT fk_middle_school_infos_applicant
        FOREIGN KEY (applicant_id) REFERENCES applicants (id)
);

CREATE TABLE academic_records (
    id                       BIGINT NOT NULL AUTO_INCREMENT,
    applicant_id             BIGINT NOT NULL,
    absent_count             INT    NOT NULL,
    late_count               INT    NOT NULL,
    early_leave_count        INT    NOT NULL,
    class_absence_count      INT    NOT NULL,
    volunteer_time           INT    NOT NULL,
    is_dsm_algorithm_awarded BIT(1) NOT NULL,
    is_programming_certified BIT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_academic_records_applicant_id (applicant_id),
    CONSTRAINT fk_academic_records_applicant
        FOREIGN KEY (applicant_id) REFERENCES applicants (id)
);

CREATE TABLE subject_grades (
    academic_record_id BIGINT      NOT NULL,
    school_semester    VARCHAR(20) NOT NULL,
    korean_grade       VARCHAR(2)  NOT NULL,
    math_grade         VARCHAR(2)  NOT NULL,
    english_grade      VARCHAR(2)  NOT NULL,
    science_grade      VARCHAR(2)  NOT NULL,
    society_grade      VARCHAR(2)  NOT NULL,
    technology_grade   VARCHAR(2)  NOT NULL,
    history_grade      VARCHAR(2)  NOT NULL,
    PRIMARY KEY (academic_record_id, school_semester),
    CONSTRAINT fk_subject_grades_academic_record
        FOREIGN KEY (academic_record_id) REFERENCES academic_records (id)
);

CREATE TABLE ged_scores (
    academic_record_id BIGINT NOT NULL,
    korean_score       INT    NOT NULL,
    math_score         INT    NOT NULL,
    english_score      INT    NOT NULL,
    science_score      INT    NOT NULL,
    society_score      INT    NOT NULL,
    technology_score   INT    NOT NULL,
    history_score      INT    NOT NULL,
    PRIMARY KEY (academic_record_id),
    CONSTRAINT fk_ged_scores_academic_record
        FOREIGN KEY (academic_record_id) REFERENCES academic_records (id)
);

CREATE TABLE pass_results (
    applicant_id BIGINT      NOT NULL,
    result_type  VARCHAR(16) NOT NULL,
    processed_by BIGINT      NULL,
    result       VARCHAR(16) NOT NULL,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (applicant_id, result_type),
    CONSTRAINT fk_pass_results_applicant
        FOREIGN KEY (applicant_id) REFERENCES applicants (id)
);
