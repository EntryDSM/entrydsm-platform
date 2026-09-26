CREATE TEMPORARY TABLE admission_quota_by_type AS
SELECT admission_type, SUM(quota) AS quota, MAX(updated_at) AS updated_at, MAX(updated_by) AS updated_by
FROM admission_quota
GROUP BY admission_type;

DELETE FROM admission_quota;
ALTER TABLE admission_quota DROP INDEX uk_admission_quota_region_type;
ALTER TABLE admission_quota DROP COLUMN region;
ALTER TABLE admission_quota ADD UNIQUE KEY uk_admission_quota_type (admission_type);

INSERT INTO admission_quota (admission_type, quota, updated_at, updated_by)
SELECT admission_type, quota, updated_at, updated_by FROM admission_quota_by_type;

DROP TEMPORARY TABLE admission_quota_by_type;
