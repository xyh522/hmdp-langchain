-- MySQL 8.0: migrate legacy customer sessions to thread-based storage.
-- Execute once while the Java application is stopped.
UPDATE cs_session SET thread_id = UUID() WHERE thread_id IS NULL;
CREATE TABLE cs_thread (
  thread_id VARCHAR(36) NOT NULL PRIMARY KEY,
  session_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE COMMENT 'legacy cs_session.id',
  user_id INT NOT NULL,
  title VARCHAR(100) NOT NULL DEFAULT '新对话',
  is_deleted TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_thread_user_update (user_id, is_deleted, update_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服对话线程';
INSERT INTO cs_thread (thread_id, session_id, user_id, title, is_deleted, create_time, update_time)
SELECT thread_id, id, user_id, title, is_deleted, create_time, update_time FROM cs_session;
ALTER TABLE cs_message ADD COLUMN thread_id VARCHAR(36) NULL AFTER id;
UPDATE cs_message m JOIN cs_session s ON m.session_id=s.id SET m.thread_id=s.thread_id;
ALTER TABLE cs_message MODIFY COLUMN thread_id VARCHAR(36) NOT NULL;
CREATE INDEX idx_message_thread_create ON cs_message (thread_id, create_time);
ALTER TABLE cs_message ADD CONSTRAINT fk_message_thread FOREIGN KEY (thread_id) REFERENCES cs_thread(thread_id);
ALTER TABLE cs_message DROP INDEX idx_session_create, DROP COLUMN session_id;
DROP TABLE cs_session;