package com.zhixing.training.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.training.api.ApiException;
import com.zhixing.training.organization.DataScopeService;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Service
public class CourseVersionService {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final DataScopeService scopes;
    private final double ratio; private final int minimum;
    public CourseVersionService(JdbcTemplate jdbc,ObjectMapper json,DataScopeService scopes,
        @Value("${zhixing.study.completion-ratio:0.8}") double ratio,@Value("${zhixing.study.min-seconds:60}") int minimum){this.jdbc=jdbc;this.json=json;this.scopes=scopes;this.ratio=ratio;this.minimum=minimum;}
    public record Snapshot(CourseDtos.CourseView course,List<LearningDtos.ChapterView> chapters){}
    public record Revision(long id,int revisionNo,String releaseNote,Instant createdAt,Long createdBy,Snapshot snapshot,boolean current){}
    public record PublishRequest(@Min(0) long version,@NotBlank @Size(max=1000) String releaseNote){}
    public record RestoreRequest(@Min(0) long version){}

    // Existing enrollments are pinned before the first content mutation, including legacy courses.
    @Transactional
    public void ensurePublished(long courseId){
        var current=lock(courseId);
        if("PUBLISHED".equals(current.status()) && currentRevision(courseId)==null) capture(courseId,null,"历史发布版本");
    }
    @Transactional
    public Revision publish(long id,long actor,PublishRequest body){
        scopes.requireContentOwner(actor,"course",id); var course=lock(id);
        if(course.version()!=body.version())throw conflict();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM course_chapter WHERE course_id=?",Integer.class,id)==0)throw new ApiException(HttpStatus.BAD_REQUEST,"EMPTY_COURSE","请先添加课程章节");
        jdbc.update("UPDATE course SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
        long revision=capture(id,actor,body.releaseNote().trim());
        return history(id,actor).stream().filter(r->r.id()==revision).findFirst().orElseThrow();
    }
    public List<Revision> history(long course,long actor){
        scopes.requireContentOwner(actor,"course",course);ensurePublished(course);Long active=currentRevision(course);
        return jdbc.query("SELECT * FROM course_revision WHERE course_id=? ORDER BY revision_no DESC",(rs,row)->new Revision(rs.getLong("id"),rs.getInt("revision_no"),rs.getString("release_note"),rs.getTimestamp("created_at").toInstant(),(Long)rs.getObject("created_by"),decode(rs.getString("snapshot_json")),Objects.equals(active,rs.getLong("id"))),course);
    }
    @Transactional
    public void restore(long course,long revision,long actor,long version){
        scopes.requireContentOwner(actor,"course",course);if(lock(course).version()!=version)throw conflict();ensurePublished(course);
        Snapshot snapshot=jdbc.query("SELECT snapshot_json FROM course_revision WHERE id=? AND course_id=?",(rs,row)->decode(rs.getString(1)),revision,course).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"REVISION_NOT_FOUND","版本不存在"));
        var c=snapshot.course();
        jdbc.update("UPDATE course SET title=?,summary=?,cover_url=?,category=?,difficulty=?,duration_minutes=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",c.title(),c.summary(),c.coverUrl(),c.category(),c.difficulty(),c.durationMinutes(),course);
        // Chapters keep stable identifiers so historical progress remains valid.
        var ids=snapshot.chapters().stream().map(LearningDtos.ChapterView::id).toList();
        for(var chapter:snapshot.chapters()){
            jdbc.update("UPDATE course_chapter SET title=?,sort_no=?,content_markdown=?,duration_minutes=? WHERE id=? AND course_id=?",chapter.title(),chapter.sortNo(),chapter.contentMarkdown(),chapter.durationMinutes(),chapter.id(),course);
        }
        if(!ids.isEmpty())jdbc.update("UPDATE course_chapter SET draft_visible=FALSE WHERE course_id=? AND id NOT IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",concat(course,ids));
        for(long chapter:ids)jdbc.update("UPDATE course_chapter SET draft_visible=TRUE WHERE id=?",chapter);
        jdbc.update("UPDATE course_resource r JOIN course_chapter ch ON ch.id=r.chapter_id SET r.status='ARCHIVED' WHERE ch.course_id=?",course);
        for(var ch:snapshot.chapters())for(var r:ch.resources())jdbc.update("UPDATE course_resource SET status='PUBLISHED' WHERE id=?",r.id());
        jdbc.update("UPDATE course SET chapter_count=? WHERE id=?",ids.size(),course);
    }
    public Snapshot forEmployee(long course,long user){
        ensurePublished(course);
        Long revision=jdbc.query("SELECT COALESCE(e.revision_id,c.published_revision_id) FROM course c LEFT JOIN course_enrollment e ON e.course_id=c.id AND e.user_id=? WHERE c.id=? AND c.status='PUBLISHED'",(rs,row)->(Long)rs.getObject(1),user,course).stream().findFirst().orElse(null);
        return revision==null?null:read(revision);
    }
    public CourseDtos.CourseView publishedCourse(CourseDtos.CourseView fallback){ensurePublished(fallback.id());Long id=currentRevision(fallback.id());return id==null?fallback:read(id).course();}
    public boolean resourceAllowed(long course,long user,long resource){var snapshot=forEmployee(course,user);return snapshot!=null&&snapshot.chapters().stream().flatMap(c->c.resources().stream()).anyMatch(r->r.id()==resource);}
    public Long currentRevision(long course){return jdbc.queryForObject("SELECT published_revision_id FROM course WHERE id=?",Long.class,course);}
    private Snapshot read(long revision){return decode(jdbc.queryForObject("SELECT snapshot_json FROM course_revision WHERE id=?",String.class,revision));}
    private CourseDtos.CourseView lock(long id){return jdbc.query("SELECT * FROM course WHERE id=? FOR UPDATE",CourseRepository::map,id).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"COURSE_NOT_FOUND","课程不存在"));}
    private long capture(long course,Long actor,String note){
        var c=lock(course);
        var chapters=jdbc.query("SELECT * FROM course_chapter WHERE course_id=? AND draft_visible=TRUE ORDER BY sort_no,id",(rs,row)->{
            long id=rs.getLong("id");int minutes=rs.getInt("duration_minutes");
            var resources=jdbc.query("SELECT * FROM course_resource WHERE chapter_id=? AND status='PUBLISHED' ORDER BY sort_no,id",(r,n)->new LearningDtos.ResourceView(r.getLong("id"),r.getString("title"),r.getString("resource_type"),r.getString("resource_url"),r.getInt("duration_seconds"),r.getLong("size_bytes"),r.getInt("sort_no"),r.getString("original_name"),r.getString("media_type"),r.getString("status")),id);
            return new LearningDtos.ChapterView(id,course,rs.getString("title"),rs.getInt("sort_no"),rs.getString("content_markdown"),minutes,Math.max(Math.max(1,minimum),(int)Math.round(minutes*60*ratio)),resources);
        },course);
        int no=jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0)+1 FROM course_revision WHERE course_id=?",Integer.class,course);
        var key=new GeneratedKeyHolder();String payload=encode(new Snapshot(c,chapters));
        jdbc.update(conn->{var ps=conn.prepareStatement("INSERT INTO course_revision(course_id,revision_no,snapshot_json,release_note,created_by) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,course);ps.setInt(2,no);ps.setString(3,payload);ps.setString(4,note);ps.setObject(5,actor);return ps;},key);
        long id=key.getKey().longValue();
        for(var ch:chapters)for(var r:ch.resources())jdbc.update("INSERT INTO course_revision_resource(revision_id,resource_id) VALUES(?,?)",id,r.id());
        jdbc.update("UPDATE course SET published_revision_id=? WHERE id=?",id,course);
        jdbc.update("UPDATE course_enrollment SET revision_id=? WHERE course_id=? AND revision_id IS NULL",id,course);
        return id;
    }
    private String encode(Snapshot s){try{return json.writeValueAsString(s);}catch(Exception e){throw new IllegalStateException(e);}}
    private Snapshot decode(String s){try{return json.readValue(s,Snapshot.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Object[] concat(long course,List<Long> ids){var args=new ArrayList<Object>();args.add(course);args.addAll(ids);return args.toArray();}
    private static ApiException conflict(){return new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","课程已更新，请刷新后重试");}
}
