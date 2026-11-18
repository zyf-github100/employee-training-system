package com.zhixing.training.report;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.organization.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ReportRepository {
    private static final int MAX_ROWS = 50_000;
    private static final String EPOCH = "1970-01-01 00:00:00";
    private final JdbcTemplate jdbc;
    private final DataScopeService scopes;

    public ReportRepository(JdbcTemplate jdbc, DataScopeService scopes) {
        this.jdbc = jdbc;
        this.scopes = scopes;
    }

    public void validate(ReportDtos.ReportQuery query) {
        if (query == null || query.from() == null || query.to() == null) return;
        if (query.from().isAfter(query.to()))
            throw bad("开始日期不能晚于结束日期");
        if (ChronoUnit.DAYS.between(query.from(), query.to()) > 731)
            throw bad("导出日期范围不能超过 2 年");
    }

    public List<ReportDtos.EmployeeRow> employees(long actor, ReportDtos.ReportQuery query) {
        String scope = scopes.resolve(actor).userCondition("u");
        StringBuilder where = new StringBuilder(" WHERE u.role_code='EMPLOYEE' AND ").append(scope);
        List<Object> args = new ArrayList<>();
        filters(where, args, query);
        String sql = "SELECT u.employee_no,u.name,COALESCE(d.name,'未分配') department_name,u.status,u.activated,"
                + "(SELECT COUNT(*) FROM course_enrollment ce WHERE ce.user_id=u.id) enrolled,"
                + "(SELECT COUNT(*) FROM course_enrollment ce WHERE ce.user_id=u.id AND ce.status='COMPLETED') completed,"
                + "(SELECT COALESCE(SUM(c.duration_minutes),0) FROM course_enrollment ce JOIN course c ON c.id=ce.course_id WHERE ce.user_id=u.id AND ce.status='COMPLETED') minutes,"
                + "(SELECT COUNT(*) FROM exam_attempt ea WHERE ea.user_id=u.id) attempts,"
                + "(SELECT COUNT(*) FROM exam_attempt ea WHERE ea.user_id=u.id AND ea.status='SUBMITTED') submitted,"
                + "(SELECT COUNT(*) FROM exam_attempt ea WHERE ea.user_id=u.id AND ea.status='SUBMITTED' AND ea.passed=TRUE) passed,"
                + "(SELECT COALESCE(ROUND(AVG(ea.score)),0) FROM exam_attempt ea WHERE ea.user_id=u.id AND ea.status='SUBMITTED') average_score,"
                + "(SELECT COUNT(*) FROM training_assignment ta WHERE ta.user_id=u.id) plans,"
                + "(SELECT COUNT(*) FROM training_assignment ta WHERE ta.user_id=u.id AND ta.status='COMPLETED') plans_completed,"
                + "(SELECT COUNT(*) FROM training_certificate tc WHERE tc.user_id=u.id) certificates,"
                + "NULLIF(GREATEST("
                + "COALESCE((SELECT MAX(ce.last_studied_at) FROM course_enrollment ce WHERE ce.user_id=u.id),'" + EPOCH + "'),"
                + "COALESCE((SELECT MAX(ea.submitted_at) FROM exam_attempt ea WHERE ea.user_id=u.id),'" + EPOCH + "'),"
                + "COALESCE((SELECT MAX(ta.completed_at) FROM training_assignment ta WHERE ta.user_id=u.id),'" + EPOCH + "')"
                + "),'" + EPOCH + "') last_active "
                + "FROM sys_user u LEFT JOIN org_department d ON d.id=u.department_id"
                + where + " ORDER BY u.employee_no LIMIT " + (MAX_ROWS + 1);
        return cap(jdbc.query(sql, (rs, row) -> new ReportDtos.EmployeeRow(
                rs.getString("employee_no"), rs.getString("name"), rs.getString("department_name"),
                rs.getString("status"), rs.getBoolean("activated"), rs.getInt("enrolled"), rs.getInt("completed"),
                rs.getInt("minutes"), rs.getInt("attempts"), rs.getInt("submitted"), rs.getInt("passed"),
                rs.getInt("average_score"), rs.getInt("plans"), rs.getInt("plans_completed"),
                rs.getInt("certificates"), instant(rs.getTimestamp("last_active"))), args.toArray()));
    }

    public List<ReportDtos.LearningRow> learning(long actor, ReportDtos.ReportQuery query) {
        String scope = scopes.resolve(actor).userCondition("u");
        StringBuilder where = new StringBuilder(" WHERE ").append(scope);
        List<Object> args = new ArrayList<>();
        filters(where, args, query);
        dateRange(where, args, "COALESCE(sp.last_reported_at,sp.updated_at)", query);
        String sql = "SELECT u.employee_no,u.name,COALESCE(d.name,'未分配') department_name,c.title course_title,"
                + "cc.title chapter_title,sp.progress_percent,sp.watched_seconds,sp.completed_at,"
                + "COALESCE(sp.last_reported_at,sp.updated_at) last_activity "
                + "FROM study_progress sp "
                + "JOIN course_enrollment ce ON ce.id=sp.enrollment_id "
                + "JOIN sys_user u ON u.id=ce.user_id "
                + "JOIN course c ON c.id=ce.course_id "
                + "JOIN course_chapter cc ON cc.id=sp.chapter_id "
                + "LEFT JOIN org_department d ON d.id=u.department_id"
                + where + " ORDER BY u.employee_no,c.title,cc.sort_no,cc.id LIMIT " + (MAX_ROWS + 1);
        return cap(jdbc.query(sql, (rs, row) -> new ReportDtos.LearningRow(
                rs.getString("employee_no"), rs.getString("name"), rs.getString("department_name"),
                rs.getString("course_title"), rs.getString("chapter_title"), rs.getInt("progress_percent"),
                rs.getInt("watched_seconds"), instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("last_activity"))), args.toArray()));
    }

    public List<ReportDtos.ExamRow> exams(long actor, ReportDtos.ReportQuery query) {
        String scope = scopes.resolve(actor).userCondition("u");
        StringBuilder where = new StringBuilder(" WHERE ").append(scope).append(" AND ea.status='SUBMITTED'");
        List<Object> args = new ArrayList<>();
        filters(where, args, query);
        dateRange(where, args, "ea.submitted_at", query);
        String sql = "SELECT u.employee_no,u.name,COALESCE(d.name,'未分配') department_name,ep.title exam_title,"
                + "ea.attempt_no,ea.status,ea.score,ea.passed,ea.started_at,ea.submitted_at,"
                + "ea.integrity_status,ea.grading_status "
                + "FROM exam_attempt ea "
                + "JOIN sys_user u ON u.id=ea.user_id "
                + "JOIN exam_paper ep ON ep.id=ea.paper_id "
                + "LEFT JOIN org_department d ON d.id=u.department_id"
                + where + " ORDER BY u.employee_no,ep.title,ea.attempt_no LIMIT " + (MAX_ROWS + 1);
        return cap(jdbc.query(sql, (rs, row) -> new ReportDtos.ExamRow(
                rs.getString("employee_no"), rs.getString("name"), rs.getString("department_name"),
                rs.getString("exam_title"), rs.getInt("attempt_no"), rs.getString("status"),
                (Integer) rs.getObject("score"), (Boolean) rs.getObject("passed"),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("submitted_at")),
                rs.getString("integrity_status"), rs.getString("grading_status")), args.toArray()));
    }

    public List<ReportDtos.PlanRow> plans(long actor, ReportDtos.ReportQuery query) {
        String scope = scopes.resolve(actor).userCondition("u");
        StringBuilder where = new StringBuilder(" WHERE ").append(scope);
        List<Object> args = new ArrayList<>();
        filters(where, args, query);
        dateRange(where, args, "COALESCE(ta.completed_at,ta.created_at)", query);
        String sql = "SELECT u.employee_no,u.name,COALESCE(d.name,'未分配') department_name,tp.title plan_title,"
                + "tp.status plan_status,ta.status assignment_status,ta.progress_percent,ta.started_at,ta.completed_at,"
                + "tp.start_date,tp.end_date,tp.termination_reason "
                + "FROM training_assignment ta "
                + "JOIN training_plan tp ON tp.id=ta.plan_id "
                + "JOIN sys_user u ON u.id=ta.user_id "
                + "LEFT JOIN org_department d ON d.id=u.department_id"
                + where + " ORDER BY u.employee_no,tp.title,ta.id LIMIT " + (MAX_ROWS + 1);
        return cap(jdbc.query(sql, (rs, row) -> new ReportDtos.PlanRow(
                rs.getString("employee_no"), rs.getString("name"), rs.getString("department_name"),
                rs.getString("plan_title"), rs.getString("plan_status"), rs.getString("assignment_status"),
                rs.getInt("progress_percent"), instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("completed_at")), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getString("termination_reason")), args.toArray()));
    }

    private static void filters(StringBuilder where, List<Object> args, ReportDtos.ReportQuery query) {
        if (query == null) return;
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String like = "%" + query.keyword().trim() + "%";
            where.append(" AND (u.employee_no LIKE ? OR u.name LIKE ?)");
            args.add(like);
            args.add(like);
        }
        if (query.departmentId() != null) {
            where.append(" AND u.department_id=?");
            args.add(query.departmentId());
        }
    }

    private static void dateRange(StringBuilder where, List<Object> args, String column, ReportDtos.ReportQuery query) {
        if (query == null) return;
        if (query.from() != null) {
            where.append(" AND ").append(column).append(">=?");
            args.add(Timestamp.valueOf(query.from().atStartOfDay()));
        }
        if (query.to() != null) {
            where.append(" AND ").append(column).append("< ?");
            args.add(Timestamp.valueOf(query.to().plusDays(1).atStartOfDay()));
        }
    }

    private <T> List<T> cap(List<T> rows) {
        if (rows.size() > MAX_ROWS)
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_TOO_LARGE", "导出数据超过 50000 行，请缩小筛选范围");
        return rows;
    }

    private static java.time.Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_QUERY", message);
    }
}
