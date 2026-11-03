CREATE TABLE org_department (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  code VARCHAR(40) NOT NULL UNIQUE,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_no VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  name VARCHAR(40) NOT NULL,
  department_id BIGINT NULL,
  job_title VARCHAR(80) NULL,
  email VARCHAR(120) NULL,
  phone VARCHAR(30) NULL,
  role_code VARCHAR(24) NOT NULL DEFAULT 'EMPLOYEE',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  activated BOOLEAN NOT NULL DEFAULT TRUE,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMP NULL,
  version BIGINT NOT NULL DEFAULT 0,
  password_changed_at TIMESTAMP NULL,
  last_login_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES org_department(id)
);

CREATE TABLE auth_session (
  id CHAR(36) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  refresh_token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at TIMESTAMP NULL,
  user_agent VARCHAR(300) NULL,
  ip_address VARCHAR(64) NULL,
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE INDEX idx_session_user ON auth_session(user_id, revoked_at, expires_at);

CREATE TABLE course (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(160) NOT NULL,
  summary VARCHAR(500) NULL,
  cover_url VARCHAR(500) NULL,
  category VARCHAR(80) NULL,
  difficulty VARCHAR(20) NOT NULL DEFAULT 'BEGINNER',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  duration_minutes INT NOT NULL DEFAULT 0,
  chapter_count INT NOT NULL DEFAULT 0,
  published_at TIMESTAMP NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_course_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
);
CREATE INDEX idx_course_status ON course(status, updated_at);

CREATE TABLE course_chapter (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  sort_no INT NOT NULL DEFAULT 1,
  content_markdown TEXT NULL,
  duration_minutes INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_chapter_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE
);
