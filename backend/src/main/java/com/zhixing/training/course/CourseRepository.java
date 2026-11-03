package com.zhixing.training.course;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Repository
public class CourseRepository {
    private final JdbcTemplate jdbc;
    public CourseRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public static CourseDtos.CourseView map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp published = rs.getTimestamp("published_at");
        return new CourseDtos.CourseView(rs.getLong("id"),rs.getString("title"),rs.getString("summary"),rs.getString("cover_url"),rs.getString("category"),rs.getString("difficulty"),rs.getString("status"),rs.getInt("duration_minutes"),rs.getInt("chapter_count"),published==null?null:published.toInstant(),rs.getLong("version"));
    }
    public List<CourseDtos.CourseView> published(int pageNo, int pageSize, String keyword) {
        int offset=(pageNo-1)*pageSize; String q=keyword==null?"":"%"+keyword.trim()+"%";
        return jdbc.query("SELECT * FROM course WHERE status='PUBLISHED' AND (?='' OR title LIKE ? OR summary LIKE ?) ORDER BY updated_at DESC LIMIT ? OFFSET ?", CourseRepository::map, q, q, q, pageSize, offset);
    }
    public long publishedCount(String keyword) { String q=keyword==null?"":"%"+keyword.trim()+"%"; Long n=jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE status='PUBLISHED' AND (?='' OR title LIKE ? OR summary LIKE ?)",Long.class,q,q,q); return n==null?0:n; }
    public List<CourseDtos.CourseView> all(int pageNo,int pageSize,String keyword) { int offset=(pageNo-1)*pageSize;String q=keyword==null?"":"%"+keyword.trim()+"%";return jdbc.query("SELECT * FROM course WHERE (?='' OR title LIKE ? OR summary LIKE ?) ORDER BY updated_at DESC LIMIT ? OFFSET ?",CourseRepository::map,q,q,q,pageSize,offset); }
    public long countAll(String keyword) { String q=keyword==null?"":"%"+keyword.trim()+"%";Long n=jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE (?='' OR title LIKE ? OR summary LIKE ?)",Long.class,q,q,q);return n==null?0:n; }
    public CourseDtos.CourseView find(long id) { return jdbc.queryForObject("SELECT * FROM course WHERE id=?",CourseRepository::map,id); }
    public long insert(CourseDtos.CourseRequest b,long creator) { String status=safeStatus(b.status()); KeyHolder key=new GeneratedKeyHolder(); jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO course(title,summary,cover_url,category,difficulty,status,duration_minutes,created_by,published_at) VALUES(?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setString(1,b.title());ps.setString(2,b.summary());ps.setString(3,b.coverUrl());ps.setString(4,b.category());ps.setString(5,safeDifficulty(b.difficulty()));ps.setString(6,status);ps.setInt(7,b.durationMinutes());ps.setLong(8,creator);if("PUBLISHED".equals(status))ps.setTimestamp(9,Timestamp.from(Instant.now()));else ps.setTimestamp(9,null);return ps;},key);return key.getKey().longValue(); }
    public boolean update(long id, CourseDtos.CourseRequest b) { return jdbc.update("UPDATE course SET title=?,summary=?,cover_url=?,category=?,difficulty=?,status=?,duration_minutes=?,version=version+1,published_at=CASE WHEN ?='PUBLISHED' AND published_at IS NULL THEN CURRENT_TIMESTAMP WHEN ?<>'PUBLISHED' THEN NULL ELSE published_at END,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?",b.title(),b.summary(),b.coverUrl(),b.category(),safeDifficulty(b.difficulty()),safeStatus(b.status()),b.durationMinutes(),safeStatus(b.status()),safeStatus(b.status()),id,b.version())==1; }
    private static String safeStatus(String s){return "PUBLISHED".equals(s)?"PUBLISHED":"DRAFT";} private static String safeDifficulty(String s){return s==null||s.isBlank()?"BEGINNER":s;}
}
