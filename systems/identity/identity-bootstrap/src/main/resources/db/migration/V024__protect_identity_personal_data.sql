ALTER TABLE accounts
    ADD COLUMN login_id_encrypted VARCHAR(255) NULL AFTER login_id_hash;
