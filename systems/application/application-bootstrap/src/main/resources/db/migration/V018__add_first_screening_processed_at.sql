-- 선발 결과와 같은 트랜잭션으로 저장하여 반복 실행과 재시작 시 중복 산출을 막는다.
ALTER TABLE schedule ADD COLUMN first_screening_processed_at DATETIME(6) NULL;
