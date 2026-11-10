ALTER TABLE sys_user ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE sys_user SET must_change_password=TRUE WHERE activated=TRUE AND password_changed_at IS NULL;

ALTER TABLE organization_batch
  ADD COLUMN before_json MEDIUMTEXT NULL,
  ADD COLUMN after_json MEDIUMTEXT NULL,
  ADD COLUMN rolled_back_at TIMESTAMP(6) NULL,
  ADD COLUMN rollback_result_json TEXT NULL;

CREATE TABLE invitation_delivery (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  channel VARCHAR(12) NOT NULL,
  destination VARCHAR(160) NOT NULL,
  setup_token_hash CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(500) NULL,
  sent_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_invitation_delivery_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);
CREATE INDEX idx_invitation_delivery_user ON invitation_delivery(user_id, created_at);
