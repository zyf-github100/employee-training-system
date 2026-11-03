CREATE TABLE training_certificate (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  certificate_no VARCHAR(80) NOT NULL UNIQUE,
  plan_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_certificate_plan_user (plan_id, user_id),
  CONSTRAINT fk_certificate_plan FOREIGN KEY (plan_id) REFERENCES training_plan(id) ON DELETE RESTRICT,
  CONSTRAINT fk_certificate_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);
