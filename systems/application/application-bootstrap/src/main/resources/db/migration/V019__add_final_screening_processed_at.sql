-- 최종 합격 발표 공개를 한 번만 처리하도록 발표 트랜잭션에서 함께 저장한다.
ALTER TABLE schedule ADD COLUMN final_screening_processed_at DATETIME(6) NULL;
