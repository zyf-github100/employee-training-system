CREATE TABLE question_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question_type VARCHAR(20) NOT NULL,
  stem TEXT NOT NULL,
  options_json JSON NOT NULL,
  answer_json JSON NOT NULL,
  analysis TEXT NULL,
  category VARCHAR(80) NOT NULL DEFAULT '通用能力',
  difficulty VARCHAR(20) NOT NULL DEFAULT '基础',
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  version BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_question_creator FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE SET NULL
);

CREATE TABLE exam_paper (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(160) NOT NULL,
  category VARCHAR(80) NOT NULL DEFAULT '综合测评',
  duration_minutes INT NOT NULL,
  pass_score INT NOT NULL DEFAULT 60,
  deadline DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  version BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  published_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_exam_creator FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE SET NULL
);

CREATE TABLE exam_paper_question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  paper_id BIGINT NOT NULL,
  question_id BIGINT NULL,
  sort_no INT NOT NULL,
  points INT NOT NULL,
  question_type VARCHAR(20) NOT NULL,
  stem TEXT NOT NULL,
  options_json JSON NOT NULL,
  answer_json JSON NOT NULL,
  analysis TEXT NULL,
  UNIQUE KEY uk_paper_question_order (paper_id, sort_no),
  CONSTRAINT fk_paper_question_paper FOREIGN KEY (paper_id) REFERENCES exam_paper(id) ON DELETE CASCADE,
  CONSTRAINT fk_paper_question_source FOREIGN KEY (question_id) REFERENCES question_item(id) ON DELETE SET NULL
);

CREATE TABLE exam_assignment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  paper_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
  max_attempts INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_exam_assignment_user (paper_id, user_id),
  CONSTRAINT fk_assignment_paper FOREIGN KEY (paper_id) REFERENCES exam_paper(id) ON DELETE CASCADE,
  CONSTRAINT fk_assignment_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);

CREATE TABLE exam_attempt (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  paper_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  attempt_no INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
  score INT NULL,
  passed BOOLEAN NULL,
  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  submitted_at TIMESTAMP NULL,
  UNIQUE KEY uk_exam_attempt_no (paper_id, user_id, attempt_no),
  CONSTRAINT fk_attempt_paper FOREIGN KEY (paper_id) REFERENCES exam_paper(id) ON DELETE CASCADE,
  CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);

CREATE TABLE exam_answer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  attempt_id BIGINT NOT NULL,
  paper_question_id BIGINT NOT NULL,
  answer_json JSON NOT NULL,
  is_correct BOOLEAN NULL,
  score INT NULL,
  answered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_attempt_question (attempt_id, paper_question_id),
  CONSTRAINT fk_answer_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempt(id) ON DELETE CASCADE,
  CONSTRAINT fk_answer_question FOREIGN KEY (paper_question_id) REFERENCES exam_paper_question(id) ON DELETE CASCADE
);
