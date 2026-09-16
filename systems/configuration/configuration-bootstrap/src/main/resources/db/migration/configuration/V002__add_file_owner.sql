ALTER TABLE files
    ADD COLUMN owner_user_id BIGINT NULL AFTER checksum;
