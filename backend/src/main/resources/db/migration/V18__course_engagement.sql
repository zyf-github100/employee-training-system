CREATE TABLE course_review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  rating TINYINT NOT NULL,
  content VARCHAR(1000) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_course_review_rating CHECK (rating BETWEEN 1 AND 5),
  CONSTRAINT uk_course_review_user UNIQUE (course_id, user_id),
  CONSTRAINT fk_course_review_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
  CONSTRAINT fk_course_review_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);
CREATE INDEX idx_course_review_course ON course_review(course_id, updated_at);

CREATE TABLE course_discussion (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  content VARCHAR(2000) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_course_discussion_status CHECK (status IN ('VISIBLE','HIDDEN')),
  CONSTRAINT fk_course_discussion_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
  CONSTRAINT fk_course_discussion_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_course_discussion_parent FOREIGN KEY (parent_id) REFERENCES course_discussion(id) ON DELETE CASCADE
);
CREATE INDEX idx_course_discussion_course ON course_discussion(course_id, status, created_at);
CREATE INDEX idx_course_discussion_parent ON course_discussion(parent_id, created_at);
