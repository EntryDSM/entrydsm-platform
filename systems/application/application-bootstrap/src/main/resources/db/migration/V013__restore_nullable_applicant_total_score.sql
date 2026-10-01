ALTER TABLE applicants MODIFY COLUMN total_score DOUBLE NULL DEFAULT NULL;

-- 계산 시각이 없는 0점만 미계산 상태로 복구한다. 계산 시각이 기록된 실제 0점은 유지한다.
-- ponytail: 계산 시각이 누락된 실제 0점도 포함된다. 원본 백업이 있으면 backfill된 ID로 한정한다.
UPDATE applicants
SET total_score = NULL
WHERE total_score = 0 AND total_score_updated_at IS NULL;
