package com.zhixing.training.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SessionRepository {
    private final JdbcTemplate jdbc;
    public SessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Session(UUID id, long userId, String hash, Instant expiresAt, Instant revokedAt) { }
    public void insert(UUID id, long userId, String hash, Instant expires, String agent, String ip) {
        jdbc.update("INSERT INTO auth_session(id,user_id,refresh_token_hash,expires_at,user_agent,ip_address) VALUES(?,?,?,?,?,?)",
                id.toString(), userId, hash, Timestamp.from(expires), agent, ip);
    }
    public Optional<Session> findActiveByHash(String hash) {
        Optional<Session> result = jdbc.query("SELECT * FROM auth_session WHERE refresh_token_hash=?", rs -> {
            if (!rs.next()) return Optional.empty();
            Timestamp revoked = rs.getTimestamp("revoked_at");
            return Optional.of(new Session(UUID.fromString(rs.getString("id")), rs.getLong("user_id"), rs.getString("refresh_token_hash"), rs.getTimestamp("expires_at").toInstant(), revoked == null ? null : revoked.toInstant()));
        }, hash);
        return result.filter(s -> s.revokedAt() == null && s.expiresAt().isAfter(Instant.now()));
    }
    public void rotate(UUID oldId, UUID newId, long userId, String newHash, Instant expires, String agent, String ip) {
        int changed = jdbc.update("UPDATE auth_session SET revoked_at=CURRENT_TIMESTAMP,last_used_at=CURRENT_TIMESTAMP WHERE id=? AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP", oldId.toString());
        if (changed != 1) throw new com.zhixing.training.api.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,"SESSION_EXPIRED","登录已过期，请重新登录");
        insert(newId, userId, newHash, expires, agent, ip);
    }
    public void revoke(UUID id) { jdbc.update("UPDATE auth_session SET revoked_at=CURRENT_TIMESTAMP WHERE id=? AND revoked_at IS NULL", id.toString()); }
    public void revokeAll(long userId) { jdbc.update("UPDATE auth_session SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL", userId); }
    public boolean isActive(UUID id, long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE id=? AND user_id=? AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP", Integer.class, id.toString(), userId);
        return n != null && n > 0;
    }
}
