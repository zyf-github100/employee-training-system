package com.zhixing.training.auth;

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
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static UserRecord map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp locked = rs.getTimestamp("locked_until");
        long dep = rs.getLong("department_id");
        Long departmentId = rs.wasNull() ? null : dep;
        return new UserRecord(rs.getLong("id"), rs.getString("employee_no"), rs.getString("password_hash"), rs.getString("name"),
                departmentId, rs.getString("department_name"), rs.getString("job_title"), rs.getString("email"),
                rs.getString("phone"), rs.getString("role_code"), rs.getString("status"), rs.getBoolean("activated"),
                rs.getInt("failed_login_count"), locked == null ? null : locked.toInstant(), rs.getLong("version"));
    }
    private static final String BASE = "SELECT u.*, d.name department_name FROM sys_user u LEFT JOIN org_department d ON d.id=u.department_id ";
    public Optional<UserRecord> findByEmployeeNo(String employeeNo) {
        List<UserRecord> rows = jdbc.query(BASE + "WHERE u.employee_no=?", UserRepository::map, employeeNo);
        return rows.stream().findFirst();
    }
    public Optional<UserRecord> lockByEmployeeNo(String employeeNo) {
        jdbc.queryForList("SELECT id FROM sys_user WHERE employee_no=? FOR UPDATE", employeeNo);
        return findByEmployeeNo(employeeNo);
    }
    public Optional<UserRecord> findById(long id) {
        List<UserRecord> rows = jdbc.query(BASE + "WHERE u.id=?", UserRepository::map, id);
        return rows.stream().findFirst();
    }
    public void recordLoginFailure(UserRecord user, int count, Instant lockedUntil) {
        jdbc.update("UPDATE sys_user SET failed_login_count=?,locked_until=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", count,
                lockedUntil == null ? null : Timestamp.from(lockedUntil), user.id());
    }
    public void recordLoginSuccess(long id) {
        jdbc.update("UPDATE sys_user SET failed_login_count=0,locked_until=NULL,last_login_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
    }
    public boolean updateProfile(long id, long version, String name, String email, String phone, String jobTitle) {
        return jdbc.update("UPDATE sys_user SET name=?,email=?,phone=?,job_title=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?",
                name, email, phone, jobTitle, id, version) == 1;
    }
    public void updatePassword(long id, String hash) {
        jdbc.update("UPDATE sys_user SET password_hash=?,password_changed_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", hash, id);
    }
    public long insert(String employeeNo, String hash, String name, String role, Long departmentId) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(conn -> { PreparedStatement ps = conn.prepareStatement("INSERT INTO sys_user(employee_no,password_hash,name,role_code,department_id) VALUES(?,?,?,?,?)", new String[]{"id"});
            ps.setString(1, employeeNo); ps.setString(2, hash); ps.setString(3, name); ps.setString(4, role); if (departmentId == null) ps.setNull(5, java.sql.Types.BIGINT); else ps.setLong(5, departmentId); return ps; }, key);
        return key.getKey().longValue();
    }
}
