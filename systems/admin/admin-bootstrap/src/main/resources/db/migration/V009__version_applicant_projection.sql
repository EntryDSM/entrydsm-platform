ALTER TABLE applicant_export_projection
    ADD COLUMN event_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    DROP INDEX uk_applicant_export_projection_account;
CREATE INDEX ix_applicant_projection_active ON applicant_export_projection (deleted);
-- 평문인 기존 파생 데이터는 버리고 최초 대사에서 암호화된 최신 원서로 재적재한다.
DELETE FROM applicant_export_projection;
