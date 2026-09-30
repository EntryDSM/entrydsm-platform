UPDATE applicants SET total_score = 0 WHERE total_score IS NULL;

ALTER TABLE applicants MODIFY COLUMN total_score DOUBLE NOT NULL DEFAULT 0;
