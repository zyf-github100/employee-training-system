package com.zhixing.training.course;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.plan.PlanRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class LearningRepository {
    private final JdbcTemplate jdbc;
    private final PlanRepository plans;
    public LearningRepository(JdbcTemplate jdbc, PlanRepository plans) { this.jdbc = jdbc; this.plans = plans; }

    public List<LearningDtos.ChapterView> chapters(long courseId, boolean publishedOnly) {
        String visibility = publishedOnly ? " AND co.status='PUBLISHED'" : "";
        List<LearningDtos.ChapterView> chapters = jdbc.query("SELECT c.id,c.course_id,c.title,c.sort_no,c.content_markdown,c.duration_minutes FROM course_chapter c JOIN course co ON co.id=c.course_id WHERE c.course_id=?" + visibility + " ORDER BY c.sort_no,c.id", (rs, row) -> new LearningDtos.ChapterView(rs.getLong("id"),rs.getLong("course_id"),rs.getString("title"),rs.getInt("sort_no"),rs.getString("content_markdown"),rs.getInt("duration_minutes"),List.of()), courseId);
        return chapters.stream().map(c -> new LearningDtos.ChapterView(c.id(), c.courseId(), c.title(), c.sortNo(), c.contentMarkdown(), c.durationMinutes(), resources(c.id(), publishedOnly))).toList();
    }
    private List<LearningDtos.ResourceView> resources(long chapterId, boolean publishedOnly) {
        String visibility = publishedOnly ? " AND status='PUBLISHED'" : "";
        return jdbc.query("SELECT id,title,resource_type,resource_url,duration_seconds,size_bytes,sort_no,original_name,media_type,status FROM course_resource WHERE chapter_id=?" + visibility + " ORDER BY sort_no,id", (rs,row) -> new LearningDtos.ResourceView(rs.getLong("id"),rs.getString("title"),rs.getString("resource_type"),rs.getString("resource_url"),rs.getInt("duration_seconds"),rs.getLong("size_bytes"),rs.getInt("sort_no"),rs.getString("original_name"),rs.getString("media_type"),rs.getString("status")), chapterId);
    }
    public long insertChapter(long courseId, LearningDtos.ChapterRequest body) {
        ensureCourse(courseId);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(conn -> { PreparedStatement ps=conn.prepareStatement("INSERT INTO course_chapter(course_id,title,sort_no,content_markdown,duration_minutes) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setLong(1,courseId);ps.setString(2,body.title());ps.setInt(3,body.sortNo());ps.setString(4,body.contentMarkdown());ps.setInt(5,body.durationMinutes());return ps; }, key);
        jdbc.update("UPDATE course SET chapter_count=(SELECT COUNT(*) FROM course_chapter WHERE course_id=?),updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?", courseId, courseId);
        return key.getKey().longValue();
    }
    public boolean updateChapter(long courseId, long chapterId, LearningDtos.ChapterRequest body) {
        boolean updated = jdbc.update("UPDATE course_chapter SET title=?,sort_no=?,content_markdown=?,duration_minutes=? WHERE id=? AND course_id=?",body.title(),body.sortNo(),body.contentMarkdown(),body.durationMinutes(),chapterId,courseId)==1;
        if (updated) jdbc.update("UPDATE course SET updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",courseId);
        return updated;
    }
    public long enrollment(long courseId, long userId) {
        jdbc.update("INSERT INTO course_enrollment(course_id,user_id) SELECT ?,? WHERE EXISTS(SELECT 1 FROM course WHERE id=? AND status='PUBLISHED') ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)",courseId,userId,courseId);
        Long id=jdbc.queryForObject("SELECT id FROM course_enrollment WHERE course_id=? AND user_id=?",Long.class,courseId,userId);
        if(id==null) throw new ApiException(HttpStatus.NOT_FOUND,"COURSE_NOT_FOUND","课程不存在或尚未发布"); return id;
    }
    public Optional<Long> findEnrollment(long courseId,long userId){return jdbc.query("SELECT id FROM course_enrollment WHERE course_id=? AND user_id=?",(rs,row)->rs.getLong(1),courseId,userId).stream().findFirst();}
    public LearningDtos.CourseDetail detail(long courseId,long userId,boolean publishedOnly){
        CourseDtos.CourseView course;
        try { course = jdbc.queryForObject("SELECT * FROM course WHERE id=?" + (publishedOnly?" AND status='PUBLISHED'":""), CourseRepository::map, courseId); } catch (Exception e) { throw new ApiException(HttpStatus.NOT_FOUND,"COURSE_NOT_FOUND","课程不存在或尚未发布"); }
        List<LearningDtos.ChapterView> list=chapters(courseId,publishedOnly); Optional<Long> enrollment=findEnrollment(courseId,userId);
        int percent=0,completed=0; if(enrollment.isPresent()){ Integer p=jdbc.queryForObject("SELECT progress_percent FROM course_enrollment WHERE id=?",Integer.class,enrollment.get());Integer c=jdbc.queryForObject("SELECT completed_chapters FROM course_enrollment WHERE id=?",Integer.class,enrollment.get());percent=p==null?0:p;completed=c==null?0:c; }
        return new LearningDtos.CourseDetail(course,list,percent,completed,enrollment.isPresent());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LearningDtos.ProgressView updateProgress(long courseId,long chapterId,long userId,LearningDtos.ProgressRequest body){
        long enrollmentId=findEnrollment(courseId,userId).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,"NOT_ENROLLED","请先报名课程"));
        jdbc.queryForObject("SELECT id FROM course_enrollment WHERE id=? FOR UPDATE", Long.class, enrollmentId);
        Integer belongs=jdbc.queryForObject("SELECT COUNT(*) FROM course_chapter cc JOIN course c ON c.id=cc.course_id WHERE cc.id=? AND cc.course_id=? AND c.status='PUBLISHED'",Integer.class,chapterId,courseId); if(belongs==null||belongs!=1)throw new ApiException(HttpStatus.NOT_FOUND,"CHAPTER_NOT_FOUND","章节不存在");
        int pct=body.completed()?100:body.progressPercent();
        jdbc.update("INSERT INTO study_progress(enrollment_id,chapter_id,progress_percent,last_position_seconds,completed_at) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE progress_percent=GREATEST(progress_percent,VALUES(progress_percent)),last_position_seconds=VALUES(last_position_seconds),completed_at=COALESCE(completed_at,VALUES(completed_at)),updated_at=CURRENT_TIMESTAMP",enrollmentId,chapterId,pct,body.lastPositionSeconds(),pct==100?Timestamp.from(Instant.now()):null);
        Integer completed=jdbc.queryForObject("SELECT COUNT(*) FROM study_progress WHERE enrollment_id=? AND progress_percent=100",Integer.class,enrollmentId); Integer total=jdbc.queryForObject("SELECT COUNT(*) FROM course_chapter WHERE course_id=?",Integer.class,courseId); int done=completed==null?0:completed; int all=total==null?0:total; int overall=all==0?0:(done*100/all);
        jdbc.update("UPDATE course_enrollment SET progress_percent=?,completed_chapters=?,status=?,last_studied_at=CURRENT_TIMESTAMP,completed_at=CASE WHEN ?=100 THEN COALESCE(completed_at,CURRENT_TIMESTAMP) ELSE completed_at END WHERE id=?",overall,done,overall==100?"COMPLETED":"ENROLLED",overall,enrollmentId);
        plans.refreshForCourse(courseId, userId);
        return progress(courseId,userId).stream().filter(item -> item.chapterId()==chapterId).findFirst().orElseThrow();
    }
    public List<LearningDtos.ProgressView> progress(long courseId,long userId){
        return jdbc.query("SELECT sp.chapter_id,sp.progress_percent,sp.last_position_seconds,sp.completed_at,sp.updated_at FROM study_progress sp JOIN course_enrollment ce ON ce.id=sp.enrollment_id WHERE ce.course_id=? AND ce.user_id=? ORDER BY sp.chapter_id",(rs,row)->new LearningDtos.ProgressView(courseId,rs.getLong("chapter_id"),rs.getInt("progress_percent"),rs.getInt("last_position_seconds"),rs.getTimestamp("completed_at")!=null,rs.getTimestamp("updated_at").toInstant()),courseId,userId);
    }
    private void ensureCourse(long id){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE id=?",Integer.class,id);if(count==null||count!=1)throw new ApiException(HttpStatus.NOT_FOUND,"COURSE_NOT_FOUND","课程不存在");}
}
