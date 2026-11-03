CREATE TABLE course_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  chapter_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  resource_type VARCHAR(20) NOT NULL,
  resource_url VARCHAR(500) NULL,
  duration_seconds INT NOT NULL DEFAULT 0,
  size_bytes BIGINT NOT NULL DEFAULT 0,
  sort_no INT NOT NULL DEFAULT 1,
  status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_resource_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapter(id) ON DELETE CASCADE
);

CREATE TABLE course_enrollment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ENROLLED',
  progress_percent INT NOT NULL DEFAULT 0,
  completed_chapters INT NOT NULL DEFAULT 0,
  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_studied_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  UNIQUE KEY uk_enrollment_course_user (course_id, user_id),
  CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
  CONSTRAINT fk_enrollment_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);

CREATE TABLE study_progress (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enrollment_id BIGINT NOT NULL,
  chapter_id BIGINT NOT NULL,
  progress_percent INT NOT NULL DEFAULT 0,
  last_position_seconds INT NOT NULL DEFAULT 0,
  completed_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_study_enrollment_chapter (enrollment_id, chapter_id),
  CONSTRAINT fk_progress_enrollment FOREIGN KEY (enrollment_id) REFERENCES course_enrollment(id) ON DELETE CASCADE,
  CONSTRAINT fk_progress_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapter(id) ON DELETE CASCADE
);

-- The two local courses already seeded in development receive real chapter rows.
INSERT INTO course_chapter(course_id,title,sort_no,content_markdown,duration_minutes)
SELECT c.id, x.title, x.sort_no, x.content_markdown, x.duration_minutes
FROM course c
JOIN (
  SELECT '新员工安全与合规' course_title, '安全与合规导学' title, 1 sort_no, '了解企业安全规范与学习目标。' content_markdown, 12 duration_minutes
  UNION ALL SELECT '新员工安全与合规', '账号与数据安全', 2, '掌握账号、密码和数据使用规范。', 18
  UNION ALL SELECT '新员工安全与合规', '完成入职安全检查', 3, '完成课程复盘与安全承诺。', 15
  UNION ALL SELECT '项目协作基础', '协作流程概览', 1, '认识项目协作的基本流程。', 15
  UNION ALL SELECT '项目协作基础', '沟通与交付规范', 2, '掌握跨部门沟通和交付规范。', 15
) x ON x.course_title=c.title
WHERE NOT EXISTS (SELECT 1 FROM course_chapter cc WHERE cc.course_id=c.id);
