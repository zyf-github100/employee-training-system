package com.zhixing.training.course;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.organization.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.zhixing.training.course.CourseDtos.*;

@Repository
public class EngagementRepository {
    private final JdbcTemplate jdbc;
    private final DataScopeService scopes;

    public EngagementRepository(JdbcTemplate jdbc, DataScopeService scopes) {
        this.jdbc = jdbc;
        this.scopes = scopes;
    }

    public ReviewSummary reviews(long courseId, long userId) {
        ensurePublishedCourse(courseId);
        Double average = jdbc.queryForObject("SELECT AVG(rating) FROM course_review WHERE course_id=?", Double.class, courseId);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM course_review WHERE course_id=?", Long.class, courseId);
        List<ReviewView> list = jdbc.query(reviewSql("WHERE r.course_id=? ORDER BY r.updated_at DESC, r.id DESC LIMIT 100"), this::review, courseId);
        ReviewView mine = jdbc.query(reviewSql("WHERE r.course_id=? AND r.user_id=?"), this::review, courseId, userId).stream().findFirst().orElse(null);
        return new ReviewSummary(average == null ? 0d : Math.round(average * 10d) / 10d, count == null ? 0 : count, mine, list);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewView upsertReview(long courseId, long userId, ReviewRequest body) {
        ensurePublishedCourse(courseId);
        requireEnrollment(courseId, userId);
        String content = body.content() == null || body.content().isBlank() ? null : body.content().trim();
        int updated = jdbc.update("UPDATE course_review SET rating=?,content=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE course_id=? AND user_id=? AND version=?",
                body.rating(), content, courseId, userId, body.version());
        if (updated == 0) {
            try {
                jdbc.update("INSERT INTO course_review(course_id,user_id,rating,content) VALUES(?,?,?,?)", courseId, userId, body.rating(), content);
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                throw conflict("评分已被其他操作更新，请刷新后重试");
            }
        }
        return jdbc.query(reviewSql("WHERE r.course_id=? AND r.user_id=?"), this::review, courseId, userId).stream().findFirst().orElseThrow();
    }

    public List<DiscussionView> discussions(long courseId, long userId, boolean admin) {
        ensurePublishedCourse(courseId);
        String visibility = admin ? "" : " AND d.status='VISIBLE'";
        return jdbc.query(discussionSql("WHERE d.course_id=?" + visibility + " ORDER BY COALESCE(d.parent_id,d.id), d.created_at, d.id"), this::discussion, courseId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DiscussionView createDiscussion(long courseId, long userId, DiscussionRequest body) {
        ensurePublishedCourse(courseId);
        requireEnrollment(courseId, userId);
        if (body.parentId() != null) {
            Long parentCourse = jdbc.query("SELECT course_id FROM course_discussion WHERE id=? AND status='VISIBLE'", (rs, row) -> rs.getLong(1), body.parentId()).stream().findFirst().orElseThrow(() -> bad("回复目标不存在或已隐藏"));
            if (parentCourse != courseId) throw bad("回复目标不属于当前课程");
        }
        KeyHolder key = new GeneratedKeyHolder();
        String content = body.content().trim();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO course_discussion(course_id,user_id,parent_id,content) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, courseId); ps.setLong(2, userId);
            if (body.parentId() == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, body.parentId());
            ps.setString(4, content); return ps;
        }, key);
        return jdbc.query(discussionSql("WHERE d.id=?"), this::discussion, key.getKey().longValue()).stream().findFirst().orElseThrow();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DiscussionView setStatus(long actor, long id, DiscussionStatusRequest body) {
        scopes.resolve(actor);
        int updated = jdbc.update("UPDATE course_discussion SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?",
                body.status(), id, body.version());
        if (updated != 1) throw conflict("讨论已被其他操作更新，请刷新后重试");
        return jdbc.query(discussionSql("WHERE d.id=?"), this::discussion, id).stream().findFirst().orElseThrow();
    }

    private void ensurePublishedCourse(long courseId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE id=? AND status='PUBLISHED'", Integer.class, courseId) != 1)
            throw new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在或尚未发布");
    }

    private void requireEnrollment(long courseId, long userId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM course_enrollment WHERE course_id=? AND user_id=?", Integer.class, courseId, userId) != 1)
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_ENROLLED", "请先报名课程");
    }

    private String reviewSql(String tail) {
        return "SELECT r.id,r.course_id,r.user_id,u.employee_no,u.name,r.rating,r.content,r.created_at,r.updated_at,r.version FROM course_review r JOIN sys_user u ON u.id=r.user_id " + tail;
    }

    private String discussionSql(String tail) {
        return "SELECT d.id,d.course_id,d.user_id,u.employee_no,u.name,d.parent_id,d.content,d.status,d.created_at,d.updated_at,d.version FROM course_discussion d JOIN sys_user u ON u.id=d.user_id " + tail;
    }

    private ReviewView review(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ReviewView(rs.getLong("id"), rs.getLong("course_id"), rs.getLong("user_id"), rs.getString("employee_no"), rs.getString("name"),
                rs.getInt("rating"), rs.getString("content"), instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"));
    }

    private DiscussionView discussion(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        long parent = rs.getLong("parent_id"); Long parentId = rs.wasNull() ? null : parent;
        return new DiscussionView(rs.getLong("id"), rs.getLong("course_id"), rs.getLong("user_id"), rs.getString("employee_no"), rs.getString("name"),
                parentId, rs.getString("content"), rs.getString("status"), instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"));
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp timestamp = rs.getTimestamp(column); return timestamp == null ? null : timestamp.toInstant();
    }
    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISCUSSION", message); }
    private static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", message); }
}
