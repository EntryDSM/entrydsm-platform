-- 사진·첨부·요강을 밖에서 찾는 ID. {종류}_{32자 임의값} 이라 순번 id 를 드러내지 않는다.
ALTER TABLE files
    ADD COLUMN public_id VARCHAR(64) NULL AFTER id;

-- 기존 행: object_key 의 종류 폴더(dsm_Entry/Backend/{종류}/...)를 접두사로 쓴다.
-- UUID() 는 시간 기반이라 이웃 값을 짐작할 수 있어 RANDOM_BYTES 를 쓴다.
UPDATE files
SET public_id = CONCAT(SUBSTRING_INDEX(SUBSTRING_INDEX(object_key, '/', 3), '/', -1), '_', LOWER(HEX(RANDOM_BYTES(16))))
WHERE public_id IS NULL;

ALTER TABLE files
    MODIFY COLUMN public_id VARCHAR(64) NOT NULL,
    ADD UNIQUE KEY uk_files_public_id (public_id);
