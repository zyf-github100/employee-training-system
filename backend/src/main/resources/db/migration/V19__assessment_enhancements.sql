ALTER TABLE question_item
  ADD COLUMN reference_answer TEXT NULL,
  ADD COLUMN grading_guide TEXT NULL;

ALTER TABLE exam_paper_question
  ADD COLUMN reference_answer TEXT NULL,
  ADD COLUMN grading_guide TEXT NULL;

ALTER TABLE exam_answer
  ADD COLUMN answer_text TEXT NULL,
  ADD COLUMN grader_id BIGINT NULL,
  ADD COLUMN grade_comment VARCHAR(1000) NULL,
  ADD COLUMN graded_at TIMESTAMP NULL,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
  ADD CONSTRAINT fk_exam_answer_grader FOREIGN KEY (grader_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE exam_attempt
  ADD COLUMN grading_status VARCHAR(20) NOT NULL DEFAULT 'AUTO',
  ADD COLUMN integrity_status VARCHAR(20) NOT NULL DEFAULT 'CLEAR';

CREATE TABLE question_import (
  id CHAR(36) PRIMARY KEY,
  actor_id BIGINT NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  result_json TEXT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_question_import_actor_hash (actor_id, payload_hash),
  CONSTRAINT fk_question_import_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id)
);
