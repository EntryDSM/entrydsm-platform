ALTER TABLE middle_school_infos
    ADD COLUMN school_code VARCHAR(20) NOT NULL;

ALTER TABLE middle_school_infos
    ADD CONSTRAINT fk_middle_school_infos_school_code
    FOREIGN KEY (school_code)
    REFERENCES institution_codes(code);