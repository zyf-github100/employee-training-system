CREATE TABLE exam_integrity_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  attempt_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  sequence_no INT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  client_at TIMESTAMP(6) NULL,
  server_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  metadata_json JSON NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_exam_integrity_sequence (attempt_id, sequence_no),
  CONSTRAINT fk_exam_integrity_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempt(id) ON DELETE CASCADE,
  CONSTRAINT fk_exam_integrity_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);
CREATE INDEX idx_exam_integrity_attempt ON exam_integrity_event(attempt_id, server_at);
