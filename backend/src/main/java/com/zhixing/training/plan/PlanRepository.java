package com.zhixing.training.plan;

import com.zhixing.training.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class PlanRepository {
    private final JdbcTemplate jdbc;
    private final com.zhixing.training.organization.DataScopeService scopes;
    private final com.zhixing.training.certificate.CertificateRepository certificates;
    public PlanRepository(JdbcTemplate jdbc, com.zhixing.training.organization.DataScopeService scopes, com.zhixing.training.certificate.CertificateRepository certificates) { this.jdbc = jdbc; this.scopes = scopes; this.certificates = certificates; }

    public List<PlanDtos.PlanView> list(boolean admin, long userId) {
        String visibility = admin ? (scopes.resolve(userId).all() ? "" : " AND tp.created_by="+userId) : " AND tp.status IN ('PUBLISHED','TERMINATED') AND EXISTS(SELECT 1 FROM training_assignment ta WHERE ta.plan_id=tp.id AND ta.user_id=?)";
        String progress = admin ? "0" : "COALESCE((SELECT progress_percent FROM training_assignment ta2 WHERE ta2.plan_id=tp.id AND ta2.user_id=?),0)";
        String assignmentCount = "(SELECT COUNT(*) FROM training_assignment ax JOIN sys_user au ON au.id=ax.user_id WHERE ax.plan_id=tp.id AND " + (admin?scopes.resolve(userId).userCondition("au"):"au.id="+userId) + ")";
        List<Object> args = admin ? List.of() : List.of(userId, userId);
        return jdbc.query("SELECT tp.id,tp.title,tp.description,tp.category,tp.start_date,tp.end_date,tp.status,tp.version,tp.terminated_at,tp.termination_reason,(SELECT COUNT(*) FROM training_plan_course pc WHERE pc.plan_id=tp.id AND pc.required=TRUE)+(SELECT COUNT(*) FROM training_plan_exam pe WHERE pe.plan_id=tp.id AND pe.required=TRUE) total_items,(SELECT COUNT(*) FROM training_plan_course pc WHERE pc.plan_id=tp.id AND pc.required=TRUE AND EXISTS(SELECT 1 FROM course_enrollment ce WHERE ce.course_id=pc.course_id AND ce.user_id=? AND ce.status='COMPLETED'))+(SELECT COUNT(*) FROM training_plan_exam pe WHERE pe.plan_id=tp.id AND pe.required=TRUE AND EXISTS(SELECT 1 FROM exam_attempt ea WHERE ea.paper_id=pe.paper_id AND ea.user_id=? AND ea.status='SUBMITTED' AND ea.passed=TRUE)) completed_items," + progress + " progress_percent," + assignmentCount + " assignee_count FROM training_plan tp WHERE 1=1" + visibility + " ORDER BY tp.id DESC", (rs,row) -> view(rs), admin ? new Object[]{userId,userId} : new Object[]{userId,userId,userId,userId});
    }

    public Optional<PlanDtos.PlanDetail> detail(long id, boolean admin, long userId) {
        String visibility = admin ? (scopes.resolve(userId).all() ? "" : " AND tp.created_by="+userId) : " AND tp.status IN ('PUBLISHED','TERMINATED') AND EXISTS(SELECT 1 FROM training_assignment ta WHERE ta.plan_id=tp.id AND ta.user_id=?)";
        List<Object> args = admin ? List.of(userId,userId,userId,id) : List.of(userId,userId,userId,id,userId);
        List<PlanDtos.PlanView> plans = jdbc.query("SELECT tp.id,tp.title,tp.description,tp.category,tp.start_date,tp.end_date,tp.status,tp.version,tp.terminated_at,tp.termination_reason,(SELECT COUNT(*) FROM training_plan_course pc WHERE pc.plan_id=tp.id AND pc.required=TRUE)+(SELECT COUNT(*) FROM training_plan_exam pe WHERE pe.plan_id=tp.id AND pe.required=TRUE) total_items,(SELECT COUNT(*) FROM training_plan_course pc WHERE pc.plan_id=tp.id AND pc.required=TRUE AND EXISTS(SELECT 1 FROM course_enrollment ce WHERE ce.course_id=pc.course_id AND ce.user_id=? AND ce.status='COMPLETED'))+(SELECT COUNT(*) FROM training_plan_exam pe WHERE pe.plan_id=tp.id AND pe.required=TRUE AND EXISTS(SELECT 1 FROM exam_attempt ea WHERE ea.paper_id=pe.paper_id AND ea.user_id=? AND ea.status='SUBMITTED' AND ea.passed=TRUE)) completed_items,COALESCE((SELECT progress_percent FROM training_assignment ta2 WHERE ta2.plan_id=tp.id AND ta2.user_id=?),0) progress_percent,(SELECT COUNT(*) FROM training_assignment ax JOIN sys_user au ON au.id=ax.user_id WHERE ax.plan_id=tp.id AND " + (admin?scopes.resolve(userId).userCondition("au"):"au.id="+userId) + ") assignee_count FROM training_plan tp WHERE tp.id=?" + visibility, (rs,row) -> view(rs), args.toArray());
        if (plans.isEmpty()) return Optional.empty();
        List<PlanDtos.PlanItemView> items = items(id, userId);
        return Optional.of(new PlanDtos.PlanDetail(plans.get(0), items));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long create(PlanDtos.PlanRequest body, long userId) {
        validateDates(body);
        Set<Long> courses = new HashSet<>(); Set<Long> exams = new HashSet<>();
        if (body.courses()!=null) for (var item:body.courses()) { if(!courses.add(item.courseId())) throw bad("培训计划不能重复绑定课程"); ensurePublishedCourse(item.courseId()); }
        if (body.exams()!=null) for (var item:body.exams()) { if(!exams.add(item.examId())) throw bad("培训计划不能重复绑定考试"); ensurePublishedExam(item.examId()); }
        if (courses.isEmpty() && exams.isEmpty()) throw bad("培训计划至少需要绑定一门课程或一场考试");
        boolean hasRequired = (body.courses()!=null && body.courses().stream().anyMatch(PlanDtos.CourseItemRequest::required))
                || (body.exams()!=null && body.exams().stream().anyMatch(PlanDtos.ExamItemRequest::required));
        if (!hasRequired) throw bad("培训计划至少需要一项必修内容");
        KeyHolder key = new GeneratedKeyHolder(); LocalDate start = body.startDate()==null?LocalDate.now():body.startDate(); LocalDate end = body.endDate()==null?start.plusDays(30):body.endDate();
        jdbc.update(connection -> { PreparedStatement ps=connection.prepareStatement("INSERT INTO training_plan(title,description,category,start_date,end_date,created_by) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setString(1,body.title().trim());ps.setString(2,body.description());ps.setString(3,blank(body.category(),"入职培训"));ps.setObject(4,start);ps.setObject(5,end);ps.setLong(6,userId);return ps; },key);
        long id=key.getKey().longValue(); int sort=1;
        if(body.courses()!=null) for(var item:body.courses()) jdbc.update("INSERT INTO training_plan_course(plan_id,course_id,sort_no,required) VALUES(?,?,?,?)",id,item.courseId(),sort++,item.required());
        sort=1; if(body.exams()!=null) for(var item:body.exams()) jdbc.update("INSERT INTO training_plan_exam(plan_id,paper_id,sort_no,required) VALUES(?,?,?,?)",id,item.examId(),sort++,item.required());
        return id;
    }

    public List<PlanDtos.EmployeeOption> eligibleEmployees(long actor) {
        return jdbc.query("SELECT u.id,u.employee_no,u.name,d.name department_name FROM sys_user u LEFT JOIN org_department d ON d.id=u.department_id WHERE u.role_code='EMPLOYEE' AND u.activated=TRUE AND u.status='ACTIVE' AND "+scopes.resolve(actor).userCondition("u")+" ORDER BY u.employee_no",
                (rs,row) -> new PlanDtos.EmployeeOption(rs.getLong("id"),rs.getString("employee_no"),rs.getString("name"),rs.getString("department_name")));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanDtos.PlanDetail publish(long id, long userId, List<Long> employeeIds) {
        scopes.lockMutations();
        scopes.requireContentOwner(userId,"training_plan",id);
        if (employeeIds == null || employeeIds.isEmpty() || employeeIds.size()>5000) throw bad("请选择目标员工");
        List<Long> targets = employeeIds.stream().distinct().sorted().toList();
        for(long target:targets) scopes.requireUser(userId,target);
        List<String> statuses = jdbc.queryForList("SELECT status FROM training_plan WHERE id=? FOR UPDATE", String.class, id);
        if (statuses.isEmpty()) throw bad("培训计划不存在");
        if ("PUBLISHED".equals(statuses.get(0))) {
            List<Long> assigned = jdbc.queryForList("SELECT user_id FROM training_assignment WHERE plan_id=? ORDER BY user_id", Long.class, id);
            if (!assigned.equals(targets)) throw new ApiException(HttpStatus.CONFLICT,"PLAN_ALREADY_PUBLISHED","已发布计划的名单不能覆盖");
            return detail(id,true,userId).orElseThrow();
        }
        if (!"DRAFT".equals(statuses.get(0))) throw bad("仅草稿计划可发布");
        List<PlanDtos.PlanItemView> content = items(id, userId);
        if (content.stream().noneMatch(PlanDtos.PlanItemView::required)) throw bad("培训计划至少需要一项必修内容");
        for (var item : content) {
            if ("COURSE".equals(item.itemType())) ensurePublishedCourse(item.itemId());
            else ensurePublishedExam(item.itemId());
        }
        for (long target : targets) {
            if (jdbc.queryForList("SELECT id FROM sys_user WHERE id=? AND role_code='EMPLOYEE' AND activated=TRUE AND status='ACTIVE' FOR UPDATE", Long.class, target).isEmpty()) throw bad("名单包含不存在或已停用的员工");
        }
        jdbc.update("UPDATE training_plan SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        for (long target : targets) {
            jdbc.update("INSERT INTO training_assignment(plan_id,user_id) VALUES(?,?)",id,target);
            jdbc.update("INSERT INTO exam_assignment(paper_id,user_id) SELECT paper_id,? FROM training_plan_exam WHERE plan_id=? ON DUPLICATE KEY UPDATE paper_id=VALUES(paper_id)", target,id);
            refreshProgress(id,target);
        }
        return detail(id,true,userId).orElseThrow();
    }

    /** 草稿发布前可整体编辑：元数据与课程/考试项一起替换，版本号避免覆盖他人修改。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanDtos.PlanDetail update(long id, PlanDtos.PlanUpdateRequest body, long userId) {
        scopes.lockMutations();
        scopes.requireContentOwner(userId,"training_plan",id);
        PlanLifecycle current=lockLifecycle(id);
        if(!"DRAFT".equals(current.status()))throw new ApiException(HttpStatus.CONFLICT,"PLAN_NOT_DRAFT","仅草稿计划可编辑");
        if(current.version()!=body.version())throw conflict();
        if(integer("SELECT COUNT(*) FROM training_assignment WHERE plan_id=?",id)>0)throw new ApiException(HttpStatus.CONFLICT,"PLAN_HAS_ASSIGNMENTS","计划已分配员工，不能编辑内容");
        LocalDate start=body.startDate()==null?current.start():body.startDate();
        LocalDate end=body.endDate()==null?current.end():body.endDate();
        if(end.isBefore(start))throw bad("结束日期不能早于开始日期");
        Set<Long> courses=new HashSet<>(), exams=new HashSet<>();
        if(body.courses()!=null)for(var item:body.courses()){if(!courses.add(item.courseId()))throw bad("培训计划不能重复绑定课程");ensurePublishedCourse(item.courseId());}
        if(body.exams()!=null)for(var item:body.exams()){if(!exams.add(item.examId()))throw bad("培训计划不能重复绑定考试");ensurePublishedExam(item.examId());}
        if(courses.isEmpty()&&exams.isEmpty())throw bad("培训计划至少需要绑定一门课程或一场考试");
        boolean hasRequired=(body.courses()!=null&&body.courses().stream().anyMatch(PlanDtos.CourseItemRequest::required))
                ||(body.exams()!=null&&body.exams().stream().anyMatch(PlanDtos.ExamItemRequest::required));
        if(!hasRequired)throw bad("培训计划至少需要一项必修内容");
        jdbc.update("UPDATE training_plan SET title=?,description=?,category=?,start_date=?,end_date=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",body.title().trim(),body.description(),blank(body.category(),"入职培训"),start,end,id);
        jdbc.update("DELETE FROM training_plan_course WHERE plan_id=?",id);
        jdbc.update("DELETE FROM training_plan_exam WHERE plan_id=?",id);
        int sort=1;
        if(body.courses()!=null)for(var item:body.courses())jdbc.update("INSERT INTO training_plan_course(plan_id,course_id,sort_no,required) VALUES(?,?,?,?)",id,item.courseId(),sort++,item.required());
        sort=1;
        if(body.exams()!=null)for(var item:body.exams())jdbc.update("INSERT INTO training_plan_exam(plan_id,paper_id,sort_no,required) VALUES(?,?,?,?)",id,item.examId(),sort++,item.required());
        return detail(id,true,userId).orElseThrow();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanDtos.PlanDetail terminate(long id, PlanDtos.TerminateRequest body, long userId) {
        scopes.lockMutations();
        scopes.requireContentOwner(userId,"training_plan",id);
        PlanLifecycle current=lockLifecycle(id);
        if(!"PUBLISHED".equals(current.status()))throw new ApiException(HttpStatus.CONFLICT,"PLAN_NOT_PUBLISHED","仅已发布计划可终止");
        if(body.reason()==null||body.reason().isBlank())throw bad("请填写终止原因");
        if(current.version()!=body.version())throw conflict();
        jdbc.update("UPDATE training_plan SET status='TERMINATED',terminated_at=CURRENT_TIMESTAMP,termination_reason=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",body.reason().trim(),id);
        return detail(id,true,userId).orElseThrow();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanDtos.PlanDetail extend(long id, PlanDtos.ExtendRequest body, long userId) {
        scopes.lockMutations();
        scopes.requireContentOwner(userId,"training_plan",id);
        PlanLifecycle current=lockLifecycle(id);
        if(!"PUBLISHED".equals(current.status()))throw new ApiException(HttpStatus.CONFLICT,"PLAN_NOT_PUBLISHED","仅已发布计划可延期");
        if(body.endDate()==null)throw bad("请选择新的结束日期");
        if(body.endDate().isBefore(current.end()))throw bad("新的结束日期不能早于当前结束日期");
        if(body.endDate().isBefore(LocalDate.now()))throw bad("新的结束日期不能早于今天");
        if(current.version()!=body.version())throw conflict();
        jdbc.update("UPDATE training_plan SET end_date=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",body.endDate(),id);
        return detail(id,true,userId).orElseThrow();
    }

    private PlanLifecycle lockLifecycle(long id){
        return jdbc.query("SELECT status,version,start_date,end_date FROM training_plan WHERE id=? FOR UPDATE",(rs,row)->new PlanLifecycle(rs.getString("status"),rs.getLong("version"),rs.getObject("start_date",LocalDate.class),rs.getObject("end_date",LocalDate.class)),id).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PLAN_NOT_FOUND","培训计划不存在"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanDtos.PlanDetail refreshProgress(long id,long userId){
        if (jdbc.queryForList("SELECT id FROM training_assignment WHERE plan_id=? AND user_id=? FOR UPDATE", Long.class,id,userId).isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND,"PLAN_NOT_FOUND","培训计划不存在或未分配");
        String planStatus=jdbc.queryForObject("SELECT status FROM training_plan WHERE id=?",String.class,id);
        if("TERMINATED".equals(planStatus))throw new ApiException(HttpStatus.CONFLICT,"PLAN_TERMINATED","培训计划已终止");
        PlanDtos.PlanDetail detail=detail(id,false,userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"PLAN_NOT_FOUND","培训计划不存在或未分配"));
        List<PlanDtos.PlanItemView> required = detail.items().stream().filter(PlanDtos.PlanItemView::required).toList();
        int total=required.size(), done=(int)required.stream().filter(item->item.progressPercent()==100).count();
        int percent=total==0?0:done*100/total;
        jdbc.update("UPDATE training_assignment SET progress_percent=?,status=?,started_at=COALESCE(started_at,CURRENT_TIMESTAMP),completed_at=CASE WHEN ?=100 THEN COALESCE(completed_at,CURRENT_TIMESTAMP) ELSE NULL END WHERE plan_id=? AND user_id=?",percent,percent==100?"COMPLETED":"IN_PROGRESS",percent,id,userId);
        if(percent==100) certificates.issue(id,userId);
        return detail(id,false,userId).orElseThrow();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void refreshForCourse(long courseId, long userId) { refreshRelated("training_plan_course", "course_id", courseId, userId); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void refreshForExam(long paperId, long userId) { refreshRelated("training_plan_exam", "paper_id", paperId, userId); }

    private void refreshRelated(String table, String column, long itemId, long userId) {
        List<Long> ids = jdbc.queryForList("SELECT ta.plan_id FROM training_assignment ta JOIN training_plan tp ON tp.id=ta.plan_id JOIN " + table + " item ON item.plan_id=tp.id WHERE tp.status='PUBLISHED' AND item." + column + "=? AND ta.user_id=? ORDER BY ta.plan_id",Long.class,itemId,userId);
        for (long id : ids) refreshProgress(id,userId);
    }

    private List<PlanDtos.PlanItemView> items(long id,long userId){
        List<PlanDtos.PlanItemView> result=new ArrayList<>();
        result.addAll(jdbc.query("SELECT 'COURSE' item_type,c.id item_id,c.title,pc.required,CASE WHEN EXISTS(SELECT 1 FROM course_enrollment ce WHERE ce.course_id=c.id AND ce.user_id=? AND ce.status='COMPLETED') THEN 100 ELSE COALESCE((SELECT progress_percent FROM course_enrollment ce WHERE ce.course_id=c.id AND ce.user_id=?),0) END progress_percent FROM training_plan_course pc JOIN course c ON c.id=pc.course_id WHERE pc.plan_id=? ORDER BY pc.sort_no,pc.id",(rs,row)->new PlanDtos.PlanItemView(rs.getString("item_type"),rs.getLong("item_id"),rs.getString("title"),rs.getBoolean("required"),rs.getInt("progress_percent"),rs.getInt("progress_percent")==100?"COMPLETED":"PENDING"),userId,userId,id));
        result.addAll(jdbc.query("SELECT 'EXAM' item_type,ep.id item_id,ep.title,pe.required,CASE WHEN EXISTS(SELECT 1 FROM exam_attempt ea WHERE ea.paper_id=ep.id AND ea.user_id=? AND ea.status='SUBMITTED' AND ea.passed=TRUE) THEN 100 ELSE 0 END progress_percent FROM training_plan_exam pe JOIN exam_paper ep ON ep.id=pe.paper_id WHERE pe.plan_id=? ORDER BY pe.sort_no,pe.id",(rs,row)->new PlanDtos.PlanItemView(rs.getString("item_type"),rs.getLong("item_id"),rs.getString("title"),rs.getBoolean("required"),rs.getInt("progress_percent"),rs.getInt("progress_percent")==100?"COMPLETED":"PENDING"),userId,id));
        return result;
    }
    private PlanDtos.PlanView view(java.sql.ResultSet rs)throws java.sql.SQLException{
        LocalDate end=rs.getObject("end_date",LocalDate.class);
        int days=(int)java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),end);
        java.sql.Timestamp terminated=rs.getTimestamp("terminated_at");
        return new PlanDtos.PlanView(rs.getLong("id"),rs.getString("title"),rs.getString("description"),rs.getString("category"),rs.getObject("start_date",LocalDate.class),end,rs.getString("status"),rs.getInt("total_items"),rs.getInt("completed_items"),rs.getInt("progress_percent"),rs.getInt("assignee_count"),rs.getLong("version"),days,terminated==null?null:terminated.toInstant(),rs.getString("termination_reason"));
    }
    private void validateDates(PlanDtos.PlanRequest b){LocalDate start=b.startDate()==null?LocalDate.now():b.startDate();LocalDate end=b.endDate()==null?start.plusDays(30):b.endDate();if(end.isBefore(start))throw bad("结束日期不能早于开始日期");}
    private void ensurePublishedCourse(long id){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE id=? AND status='PUBLISHED'",Integer.class,id);if(n==null||n!=1)throw bad("只能绑定已发布课程");}
    private void ensurePublishedExam(long id){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM exam_paper WHERE id=? AND status='PUBLISHED'",Integer.class,id);if(n==null||n!=1)throw bad("只能绑定已发布考试");}
    private int integer(String sql,Object...args){Integer n=jdbc.queryForObject(sql,Integer.class,args);return n==null?0:n;}
    private static ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PLAN",message);}
    private static ApiException conflict(){return new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","培训计划已被其他人修改，请刷新后重试");}
    private record PlanLifecycle(String status,long version,LocalDate start,LocalDate end){ }
    private static String blank(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
}
