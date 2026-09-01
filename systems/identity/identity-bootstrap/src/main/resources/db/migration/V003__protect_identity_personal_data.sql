-- Existing plaintext personal data is intentionally not converted or read.
-- New writes populate the encrypted column; rows without it remain unavailable.
ALTER TABLE accounts
    ADD COLUMN login_id_encrypted VARCHAR(255) NULL AFTER login_id_hash;
