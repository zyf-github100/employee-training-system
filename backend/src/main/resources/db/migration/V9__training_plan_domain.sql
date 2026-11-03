CREATE TABLE training_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(160) NOT NULL,
  description VARCHAR(500) NULL,
  category VARCHAR(80) NOT NULL DEFAULT '入职培训',
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  version BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  published_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_plan_creator FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE SET NULL
);

CREATE TABLE training_plan_course (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  sort_no INT NOT NULL DEFAULT 1,
  required BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_plan_course (plan_id, course_id),
  CONSTRAINT fk_plan_course_plan FOREIGN KEY (plan_id) REFERENCES training_plan(id) ON DELETE CASCADE,
  CONSTRAINT fk_plan_course_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE RESTRICT
);

CREATE TABLE training_plan_exam (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  paper_id BIGINT NOT NULL,
  sort_no INT NOT NULL DEFAULT 1,
  required BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_plan_exam (plan_id, paper_id),
  CONSTRAINT fk_plan_exam_plan FOREIGN KEY (plan_id) REFERENCES training_plan(id) ON DELETE CASCADE,
  CONSTRAINT fk_plan_exam_paper FOREIGN KEY (paper_id) REFERENCES exam_paper(id) ON DELETE RESTRICT
);

CREATE TABLE training_assignment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
  progress_percent INT NOT NULL DEFAULT 0,
  started_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_training_assignment_user (plan_id, user_id),
  CONSTRAINT fk_training_assignment_plan FOREIGN KEY (plan_id) REFERENCES training_plan(id) ON DELETE CASCADE,
  CONSTRAINT fk_training_assignment_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);
