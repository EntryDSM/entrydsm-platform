-- 검정고시 기술가정은 선택 과목이며 역사는 반영하지 않는다.
ALTER TABLE ged_scores
    MODIFY COLUMN technology_score INT NULL DEFAULT NULL;

ALTER TABLE ged_scores
    DROP COLUMN history_score;
