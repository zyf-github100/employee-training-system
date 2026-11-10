CREATE TABLE course_revision (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  revision_no INT NOT NULL,
  snapshot_json MEDIUMTEXT NOT NULL,
  release_note VARCHAR(1000) NOT NULL,
  created_by BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_course_revision(course_id,revision_no),
  FOREIGN KEY(course_id) REFERENCES course(id)
);
ALTER TABLE course ADD published_revision_id BIGINT NULL;
ALTER TABLE course_enrollment ADD revision_id BIGINT NULL;
ALTER TABLE course_chapter ADD draft_visible BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE course_revision_resource (
  revision_id BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  PRIMARY KEY(revision_id,resource_id),
  FOREIGN KEY(revision_id) REFERENCES course_revision(id),
  FOREIGN KEY(resource_id) REFERENCES course_resource(id)
);
