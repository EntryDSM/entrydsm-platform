-- document 증명사진 ID 가 숫자에서 photo_{임의값} 문자열로 바뀌었다.
-- 예전 숫자 값은 그대로 남지만 새 ID 가 아니라서 수험표 사진으로 이어지지 않는다.
ALTER TABLE applicants
    MODIFY COLUMN photo_file_id VARCHAR(64) NULL;
