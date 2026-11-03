package com.zhixing.training.course;

import com.zhixing.training.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

@Repository
public class ResourceRepository {
    private final JdbcTemplate jdbc;

    public ResourceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public LearningDtos.ResourceView insert(long chapterId, String title, int sortNo, ResourceStorageService.StoredFile file) {
        ensureChapter(chapterId);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO course_resource(chapter_id,title,resource_type,resource_url,duration_seconds,size_bytes,sort_no,status,original_name,media_type,storage_key,sha256) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, chapterId);
            ps.setString(2, title == null || title.isBlank() ? file.originalName() : title.trim());
            ps.setString(3, file.resourceType());
            ps.setString(4, null);
            ps.setInt(5, 0);
            ps.setLong(6, file.sizeBytes());
            ps.setInt(7, Math.max(1, sortNo));
            ps.setString(8, "PUBLISHED");
            ps.setString(9, file.originalName());
            ps.setString(10, file.mediaType());
            ps.setString(11, file.storageKey());
            ps.setString(12, file.sha256());
            return ps;
        }, key);
        long id = key.getKey().longValue();
        String url = "/api/v1/resources/" + id + "/content";
        jdbc.update("UPDATE course_resource SET resource_url=? WHERE id=?", url, id);
        return find(id).orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_SAVE_FAILED", "资源保存失败"));
    }

    public Optional<ResourceRecord> findRecord(long resourceId) {
        return jdbc.query("SELECT r.id,r.chapter_id,c.course_id,co.status course_status,r.title,r.resource_type,r.resource_url,r.size_bytes,r.original_name,r.media_type,r.status,r.storage_key FROM course_resource r JOIN course_chapter c ON c.id=r.chapter_id JOIN course co ON co.id=c.course_id WHERE r.id=?", (rs, row) -> new ResourceRecord(rs.getLong("id"),rs.getLong("chapter_id"),rs.getLong("course_id"),rs.getString("course_status"),rs.getString("title"),rs.getString("resource_type"),rs.getString("resource_url"),rs.getLong("size_bytes"),rs.getString("original_name"),rs.getString("media_type"),rs.getString("status"),rs.getString("storage_key")), resourceId).stream().findFirst();
    }

    public Optional<LearningDtos.ResourceView> find(long resourceId) {
        return jdbc.query("SELECT id,title,resource_type,resource_url,duration_seconds,size_bytes,sort_no,original_name,media_type,status FROM course_resource WHERE id=?", (rs, row) -> new LearningDtos.ResourceView(rs.getLong("id"),rs.getString("title"),rs.getString("resource_type"),rs.getString("resource_url"),rs.getInt("duration_seconds"),rs.getLong("size_bytes"),rs.getInt("sort_no"),rs.getString("original_name"),rs.getString("media_type"),rs.getString("status")), resourceId).stream().findFirst();
    }

    public Optional<ResourceRecord> delete(long resourceId) {
        Optional<ResourceRecord> record = findRecord(resourceId);
        if (record.isEmpty()) return Optional.empty();
        jdbc.update("DELETE FROM course_resource WHERE id=?", resourceId);
        return record;
    }

    public boolean enrolled(long courseId, long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM course_enrollment WHERE course_id=? AND user_id=?", Integer.class, courseId, userId);
        return count != null && count > 0;
    }

    private void ensureChapter(long chapterId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM course_chapter WHERE id=?", Integer.class, chapterId);
        if (count == null || count != 1) throw new ApiException(HttpStatus.NOT_FOUND, "CHAPTER_NOT_FOUND", "章节不存在");
    }

    public record ResourceRecord(long id, long chapterId, long courseId, String courseStatus, String title, String resourceType,
                                 String resourceUrl, long sizeBytes, String originalName, String mediaType, String status, String storageKey) { }
}
