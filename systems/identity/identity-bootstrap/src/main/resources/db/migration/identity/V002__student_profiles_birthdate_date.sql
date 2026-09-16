-- Precondition: verify that every existing value matches YYYY-MM-DD before applying.
-- Invalid rows must be corrected or quarantined before this statement is executed.
ALTER TABLE student_profiles
    MODIFY COLUMN birthdate DATE NOT NULL;
