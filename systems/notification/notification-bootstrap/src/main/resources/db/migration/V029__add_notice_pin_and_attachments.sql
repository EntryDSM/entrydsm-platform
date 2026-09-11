ALTER TABLE notices
    ADD COLUMN is_pinned BIT(1) NOT NULL DEFAULT b'0',
    ADD COLUMN attachment_ids TEXT NULL;
