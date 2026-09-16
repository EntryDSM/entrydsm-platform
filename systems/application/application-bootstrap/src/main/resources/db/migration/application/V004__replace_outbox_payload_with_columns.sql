-- 상태 이벤트를 Redis Stream(protobuf) 대신 같은 프로세스 안에서 전달한다.
-- protobuf 로 직렬화한 payload 를 버리고 이벤트 필드를 컬럼으로 저장한다.
-- 기존 행의 payload 는 SQL 로 풀 수 없고 전달 대기 이벤트는 일시 데이터이므로 비우고 시작한다.
DELETE FROM applicant_status_outbox;

ALTER TABLE applicant_status_outbox
    DROP COLUMN payload,
    ADD COLUMN applicant_status VARCHAR(16) NOT NULL AFTER account_id,
    ADD COLUMN pass_status VARCHAR(16) NOT NULL AFTER applicant_status,
    ADD COLUMN status_version BIGINT NOT NULL AFTER pass_status,
    ADD COLUMN submitted_at DATETIME(6) NULL AFTER status_version,
    ADD COLUMN announced_at DATETIME(6) NULL AFTER submitted_at;
