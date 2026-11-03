UPDATE course c
SET chapter_count = (
    SELECT COUNT(*)
    FROM course_chapter cc
    WHERE cc.course_id = c.id
);
