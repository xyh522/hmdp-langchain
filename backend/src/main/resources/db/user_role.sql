-- Existing accounts remain ordinary users. Assign ADMIN only to a designated account.
ALTER TABLE tb_user ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'USER or ADMIN';