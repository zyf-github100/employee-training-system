package com.zhixing.training.exam;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.organization.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static com.zhixing.training.exam.ExamDtos.*;

@Service
public class ExamIntegrityService {
    private final JdbcTemplate jdbc;
    private final DataScopeService scopes;
    public ExamIntegrityService(JdbcTemplate jdbc, DataScopeService scopes) { this.jdbc = jdbc; this.scopes = scopes; }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IntegrityView record(long userId, long attemptId, IntegrityEventRequest body) {
        var owner = jdbc.query("SELECT user_id,status FROM exam_attempt WHERE id=? FOR UPDATE", (rs,row) -> new Owner(rs.getLong("user_id"),rs.getString("status")), attemptId).stream().findFirst().orElseThrow(() -> notFound("答卷不存在"));
        if (owner.userId() != userId) throw notFound("答卷不存在");
        if (!"IN_PROGRESS".equals(owner.status())) return view(attemptId);
        int sequence = jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM exam_integrity_event WHERE attempt_id=?", Integer.class, attemptId);
        jdbc.update("INSERT INTO exam_integrity_event(attempt_id,user_id,sequence_no,event_type,client_at,metadata_json) VALUES(?,?,?,?,?,?)", attemptId, userId, sequence, body.eventType().trim().toUpperCase(), body.clientAt() == null ? null : Timestamp.from(body.clientAt()), body.metadata());
        int suspicious = jdbc.queryForObject("SELECT COUNT(*) FROM exam_integrity_event WHERE attempt_id=? AND event_type IN ('VISIBILITY_HIDDEN','FOCUS_LOST','CONCURRENT_SESSION','CLOCK_DRIFT')", Integer.class, attemptId);
        if (suspicious >= 5 || "CONCURRENT_SESSION".equalsIgnoreCase(body.eventType())) jdbc.update("UPDATE exam_attempt SET integrity_status='REVIEW' WHERE id=? AND integrity_status='CLEAR'", attemptId);
        return view(attemptId);
    }

    public IntegrityView view(long attemptId) {
        String status = jdbc.queryForObject("SELECT integrity_status FROM exam_attempt WHERE id=?", String.class, attemptId);
        List<IntegrityEventView> events = jdbc.query("SELECT id,sequence_no,event_type,client_at,server_at,metadata_json FROM exam_integrity_event WHERE attempt_id=? ORDER BY sequence_no", (rs,row) -> new IntegrityEventView(rs.getLong("id"),rs.getInt("sequence_no"),rs.getString("event_type"),instant(rs,"client_at"),instant(rs,"server_at"),rs.getString("metadata_json")), attemptId);
        return new IntegrityView(attemptId, status, events);
    }
    public IntegrityView view(long attemptId, long actor) {
        long paperId = jdbc.queryForObject("SELECT paper_id FROM exam_attempt WHERE id=?", Long.class, attemptId);
        scopes.requireContentOwner(actor, "exam_paper", paperId);
        return view(attemptId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IntegrityView disposition(long actor, long attemptId, IntegrityDispositionRequest body) {
        long paperId = jdbc.queryForObject("SELECT paper_id FROM exam_attempt WHERE id=?", Long.class, attemptId);
        scopes.requireContentOwner(actor, "exam_paper", paperId);
        jdbc.update("UPDATE exam_attempt SET integrity_status=? WHERE id=?", body.status(), attemptId);
        if (body.reason() != null && !body.reason().isBlank())
            jdbc.update("INSERT INTO exam_integrity_event(attempt_id,user_id,sequence_no,event_type,metadata_json) SELECT ?,?,?,?,JSON_OBJECT('reason',?)", attemptId, actor, jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM exam_integrity_event WHERE attempt_id=?", Integer.class, attemptId), "ADMIN_DISPOSITION", body.reason());
        return view(attemptId);
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException { Timestamp t=rs.getTimestamp(column); return t==null?null:t.toInstant(); }
    private static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND,"ATTEMPT_NOT_FOUND",message); }
    private record Owner(long userId,String status) { }
}
