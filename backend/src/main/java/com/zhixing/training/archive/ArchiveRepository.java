package com.zhixing.training.archive;

import com.zhixing.training.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class ArchiveRepository {
    private final JdbcTemplate jdbc;
    private final com.zhixing.training.organization.DataScopeService scopes;
    public ArchiveRepository(JdbcTemplate jdbc,com.zhixing.training.organization.DataScopeService scopes) { this.jdbc = jdbc; this.scopes=scopes; }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ArchiveDtos.EmployeeArchive adminEmployee(long actor, long target) {
        scopes.requireUser(actor,target);
        return employee(target);
    }

    public ArchiveDtos.EmployeeArchive employee(long userId) {
        User user = jdbc.query("SELECT u.employee_no,u.name,d.name department_name FROM sys_user u LEFT JOIN org_department d ON d.id=u.department_id WHERE u.id=?", (rs,row) -> new User(rs.getString("employee_no"),rs.getString("name"),rs.getString("department_name")), userId).stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","用户不存在"));
        int enrolled = integer("SELECT COUNT(*) FROM course_enrollment WHERE user_id=?", userId);
        int completed = integer("SELECT COUNT(*) FROM course_enrollment WHERE user_id=? AND status='COMPLETED'", userId);
        int averageProgress = integer("SELECT COALESCE(ROUND(AVG(progress_percent)),0) FROM course_enrollment WHERE user_id=?", userId);
        int completedMinutes = integer("SELECT COALESCE(SUM(c.duration_minutes),0) FROM course_enrollment ce JOIN course c ON c.id=ce.course_id WHERE ce.user_id=? AND ce.status='COMPLETED'", userId);
        int attempts = integer("SELECT COUNT(*) FROM exam_attempt WHERE user_id=?", userId);
        int submitted = integer("SELECT COUNT(*) FROM exam_attempt WHERE user_id=? AND status='SUBMITTED'", userId);
        int passed = integer("SELECT COUNT(*) FROM exam_attempt WHERE user_id=? AND status='SUBMITTED' AND passed=TRUE", userId);
        int averageScore = integer("SELECT COALESCE(ROUND(AVG(score)),0) FROM exam_attempt WHERE user_id=? AND status='SUBMITTED'", userId);
        int assigned = integer("SELECT COUNT(*) FROM training_assignment WHERE user_id=?", userId);
        int planCompleted = integer("SELECT COUNT(*) FROM training_assignment WHERE user_id=? AND status='COMPLETED'", userId);
        int planAverage = integer("SELECT COALESCE(ROUND(AVG(progress_percent)),0) FROM training_assignment WHERE user_id=?", userId);
        List<ArchiveDtos.RecordView> records = jdbc.query("SELECT record_type,record_id,title,status,progress_percent,score,updated_at FROM (SELECT 'COURSE' record_type,c.id record_id,c.title,ce.status,ce.progress_percent,NULL score,COALESCE(ce.last_studied_at,ce.started_at) updated_at FROM course_enrollment ce JOIN course c ON c.id=ce.course_id WHERE ce.user_id=? UNION ALL SELECT 'EXAM',ea.id,ep.title,ea.status,CASE WHEN ea.status='SUBMITTED' THEN 100 ELSE 0 END,ea.score,COALESCE(ea.submitted_at,ea.started_at) FROM exam_attempt ea JOIN exam_paper ep ON ep.id=ea.paper_id WHERE ea.user_id=? UNION ALL SELECT 'PLAN',ta.id,tp.title,ta.status,ta.progress_percent,NULL,ta.created_at FROM training_assignment ta JOIN training_plan tp ON tp.id=ta.plan_id WHERE ta.user_id=?) x ORDER BY updated_at DESC LIMIT 10", (rs,row) -> new ArchiveDtos.RecordView(rs.getString("record_type"),rs.getLong("record_id"),rs.getString("title"),rs.getString("status"),rs.getInt("progress_percent"),(Integer)rs.getObject("score"),timestamp(rs.getTimestamp("updated_at"))), userId,userId,userId);
        return new ArchiveDtos.EmployeeArchive(user.employeeNo(),user.name(),user.departmentName(),new ArchiveDtos.CourseSummary(enrolled,completed,averageProgress,completedMinutes),new ArchiveDtos.ExamSummary(attempts,submitted,passed,averageScore),new ArchiveDtos.PlanSummary(assigned,planCompleted,planAverage),records);
    }

    public ArchiveDtos.AnalyticsSummary analytics(long actor) {
        var scope=scopes.resolve(actor);String condition=scope.userCondition("u");
        int employees=integer("SELECT COUNT(*) FROM sys_user u WHERE u.role_code='EMPLOYEE' AND u.activated=TRUE AND u.status='ACTIVE' AND "+condition);
        int courses=integer("SELECT COUNT(*) FROM course WHERE status='PUBLISHED'");
        int enrollments=scopedCount("course_enrollment","",condition), courseCompleted=scopedCount("course_enrollment"," AND r.status='COMPLETED'",condition);
        int exams=integer("SELECT COUNT(*) FROM exam_paper WHERE status='PUBLISHED'");
        int submitted=scopedCount("exam_attempt"," AND r.status='SUBMITTED'",condition),passed=scopedCount("exam_attempt"," AND r.status='SUBMITTED' AND r.passed=TRUE",condition);
        int plans=integer("SELECT COUNT(*) FROM training_plan WHERE status='PUBLISHED'"+(scope.all()?"":" AND created_by="+actor));
        int assignments=scopedCount("training_assignment","",condition),completedPlans=scopedCount("training_assignment"," AND r.status='COMPLETED'",condition);
        return new ArchiveDtos.AnalyticsSummary(employees,courses,enrollments,courseCompleted,rate(courseCompleted,enrollments),exams,submitted,passed,rate(passed,submitted),plans,assignments,completedPlans,rate(completedPlans,assignments));
    }
    private int scopedCount(String table,String extra,String scope) { return integer("SELECT COUNT(*) FROM "+table+" r JOIN sys_user u ON u.id=r.user_id WHERE "+scope+extra); }
    private int integer(String sql,Object...args){Integer value=jdbc.queryForObject(sql,Integer.class,args);return value==null?0:value;}
    private int rate(int numerator,int denominator){return denominator==0?0:Math.round(numerator*100f/denominator);}
    private Instant timestamp(Timestamp value){return value==null?null:value.toInstant();}
    private record User(String employeeNo,String name,String departmentName) { }
}
