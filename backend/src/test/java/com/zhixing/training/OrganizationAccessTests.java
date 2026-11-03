package com.zhixing.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.training.api.ApiException;
import com.zhixing.training.auth.*;
import com.zhixing.training.archive.ArchiveRepository;
import com.zhixing.training.certificate.CertificateRepository;
import com.zhixing.training.organization.*;
import com.zhixing.training.plan.*;
import com.zhixing.training.exam.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static com.zhixing.training.organization.OrganizationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class OrganizationAccessTests {
    @Autowired JdbcTemplate jdbc;@Autowired OrganizationService org;@Autowired DataScopeService scopes;
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired PasswordEncoder encoder;@Autowired AuthService auth;
    @Autowired PlanRepository plans;@Autowired ExamRepository exams;@Autowired ArchiveRepository archive;@Autowired CertificateRepository certificates;
    long root,a,child,b,manager,local,descendant,foreign;String tag;static String hash;
    @BeforeEach void fixture() {
        if(hash==null)hash=encoder.encode("OrgTest@123");tag=UUID.randomUUID().toString().substring(0,8);
        root=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no='ADMIN001'",Long.class);
        a=dept(null,"A");child=dept(a,"C");b=dept(null,"B");
        manager=user(a,"ADMIN","DEPARTMENT_TREE","M");local=user(a,"EMPLOYEE","SELF","L");descendant=user(child,"EMPLOYEE","SELF","D");foreign=user(b,"EMPLOYEE","SELF","F");
    }
    long dept(Long parent,String suffix) {return org.saveDepartment(root,null,new DepartmentRequest(parent,"QA_"+tag+suffix,"Dept "+suffix,"ACTIVE",0,0));}
    long user(long dept,String role,String scope,String suffix) {
        String no="O"+tag+suffix;jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name,department_id,role_code,data_scope) VALUES(?,?,?,?,?,?)",no,hash,"User "+suffix,dept,role,scope);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);
    }
    EmployeeRequest body(long id,String role,boolean superAdmin,String scope,long dept,String status,long version) {
        var e=org.employee(root,id);return new EmployeeRequest(e.employeeNo(),e.name(),dept,e.jobTitle(),e.email(),e.phone(),role,superAdmin,scope,List.of(),status,true,null,version);
    }
    EmployeeRequest same(long id,String status) {var e=org.employee(root,id);return body(id,e.roleCode(),e.superAdmin(),e.dataScope(),e.departmentId(),status,e.version());}
    RequestPostProcessor actor(long id,String role){return jwt().jwt(j->j.subject(""+id)).authorities(new SimpleGrantedAuthority("ROLE_"+role));}
    void denied(Runnable action){assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(403));}
    long plan(long actor) {
        long course=jdbc.queryForObject("SELECT id FROM course WHERE status='PUBLISHED' ORDER BY id LIMIT 1",Long.class);
        return plans.create(new PlanDtos.PlanRequest("Scoped "+tag,"Scope regression","QA",LocalDate.now(),LocalDate.now().plusDays(3),List.of(new PlanDtos.CourseItemRequest(course,true)),List.of()),actor);
    }
    @Test void adminArchivesUseCurrentPersonnelScope() throws Exception {
        mvc.perform(get("/api/v1/admin/employees/"+descendant+"/archive").with(actor(manager,"ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.employeeNo").value("O"+tag+"D"))
                .andExpect(jsonPath("$.data.courses.enrolled").value(0));
        mvc.perform(get("/api/v1/admin/employees/"+foreign+"/archive").with(actor(manager,"ADMIN"))).andExpect(status().isForbidden());
        jdbc.update("UPDATE sys_user SET data_scope='SELF' WHERE id=?",manager);
        mvc.perform(get("/api/v1/admin/employees/"+local+"/archive").with(actor(manager,"ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/employees/"+manager+"/archive").with(actor(manager,"ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/employees/"+foreign+"/archive").with(actor(root,"ADMIN"))).andExpect(status().isOk());
    }
    @Test void employeeAndAnonymousCannotReadManagementArchives() throws Exception {
        mvc.perform(get("/api/v1/admin/employees/"+local+"/archive").with(actor(local,"EMPLOYEE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/employees/"+local+"/archive")).andExpect(status().isUnauthorized());
    }
    @Test void departmentTreeRejectsCyclesStaleEditsAndInvalidDisabling() {
        assertThatThrownBy(()->org.saveDepartment(root,a,new DepartmentRequest(child,"QA_"+tag+"A","Cycle","ACTIVE",0,0))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->org.saveDepartment(root,b,new DepartmentRequest(null,"QA_"+tag+"B","Busy","DISABLED",0,0))).isInstanceOf(ApiException.class);
        long empty=dept(null,"E");org.saveDepartment(root,empty,new DepartmentRequest(null,"QA_"+tag+"E","Empty","DISABLED",0,0));
        assertThatThrownBy(()->org.saveDepartment(root,empty,new DepartmentRequest(null,"QA_"+tag+"E","Stale","ACTIVE",0,0))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("VERSION_CONFLICT"));
        assertThatThrownBy(()->org.saveDepartment(root,null,new DepartmentRequest(empty,"QA_"+tag+"X","Invalid child","ACTIVE",0,0))).isInstanceOf(ApiException.class);
        denied(()->org.saveDepartment(manager,null,new DepartmentRequest(null,"NEW","Forbidden","ACTIVE",0,0)));
    }
    @Test void fiveScopesAreResolvedFromCurrentDatabaseState() {
        assertThat(org.departments(manager)).extracting(Department::id).containsExactly(a,child);
        assertThat(plans.eligibleEmployees(manager)).extracting(PlanDtos.EmployeeOption::id).containsExactlyInAnyOrder(local,descendant);
        jdbc.update("UPDATE sys_user SET data_scope='DEPARTMENT' WHERE id=?",manager);
        assertThat(plans.eligibleEmployees(manager)).extracting(PlanDtos.EmployeeOption::id).containsExactly(local);
        jdbc.update("UPDATE sys_user SET data_scope='CUSTOM' WHERE id=?",manager);jdbc.update("INSERT INTO user_scope_department VALUES(?,?)",manager,b);
        assertThat(plans.eligibleEmployees(manager)).extracting(PlanDtos.EmployeeOption::id).containsExactly(foreign);
        jdbc.update("UPDATE sys_user SET data_scope='SELF' WHERE id=?",manager);
        assertThat(org.employees(manager,1,20,"",null,null,null).list()).extracting(Employee::id).containsExactly(manager);
        assertThat(plans.eligibleEmployees(manager)).isEmpty();
        jdbc.update("UPDATE sys_user SET data_scope='ALL' WHERE id=?",manager);
        assertThat(plans.eligibleEmployees(manager)).extracting(PlanDtos.EmployeeOption::id).contains(local,descendant,foreign);
    }
    @Test void scopedAdminCannotEscalateOrMoveOrModifyOutsideEmployees() {
        denied(()->org.saveEmployee(manager,foreign,same(foreign,"DISABLED")));
        denied(()->org.saveEmployee(manager,local,body(local,"ADMIN",false,"ALL",a,"ACTIVE",0)));
        denied(()->org.saveEmployee(manager,local,body(local,"EMPLOYEE",false,"SELF",b,"ACTIVE",0)));
        denied(()->org.saveEmployee(manager,manager,same(manager,"DISABLED")));
        assertThat(org.saveEmployee(manager,local,same(local,"DISABLED")).status()).isEqualTo("DISABLED");
    }
    @Test void createUsesHashAndEnforcesDuplicateEmployeeNumberAndVersion() throws Exception {
        var request=new EmployeeRequest("NEW_"+tag,"New employee",a,"QA","new@example.test","","EMPLOYEE",false,"SELF",List.of(),"ACTIVE",true,"UniqueInitial@123",0);
        var created=org.saveEmployee(manager,null,request);
        assertThat(encoder.matches(request.initialPassword(),jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=?",String.class,created.id()))).isTrue();
        assertThat(json.writeValueAsString(created)).doesNotContain("UniqueInitial","password");
        assertThatThrownBy(()->org.saveEmployee(manager,null,request)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("EMPLOYEE_NO_EXISTS"));
        org.saveEmployee(manager,created.id(),same(created.id(),"ACTIVE"));
        assertThatThrownBy(()->org.saveEmployee(manager,created.id(),body(created.id(),"EMPLOYEE",false,"SELF",a,"ACTIVE",created.version()))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("VERSION_CONFLICT"));
    }
    @Test void disablingRevokesRealAccessTokenRefreshAndSubsequentLogin() throws Exception {
        var issued=auth.login("O"+tag+"L","OrgTest@123","test","127.0.0.1");
        String token=issued.response().accessToken();
        mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        org.saveEmployee(manager,local,same(local,"DISABLED"));
        mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->auth.refresh(issued.refreshToken(),"test","127.0.0.1")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->auth.login("O"+tag+"L","OrgTest@123","test","127.0.0.1")).isInstanceOf(ApiException.class);
    }
    @Test void scopeChangeRevokesSessionsAndUnlockClearsFailureCounter() throws Exception {
        var issued=auth.login("O"+tag+"M","OrgTest@123","test","127.0.0.1");
        org.saveEmployee(root,manager,body(manager,"ADMIN",false,"DEPARTMENT",a,"ACTIVE",0));
        mvc.perform(get("/api/v1/admin/access").header("Authorization","Bearer "+issued.response().accessToken())).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE sys_user SET failed_login_count=5,locked_until=TIMESTAMPADD(MINUTE,15,CURRENT_TIMESTAMP) WHERE id=?",local);
        assertThatThrownBy(()->auth.login("O"+tag+"L","OrgTest@123","test","127.0.0.1")).isInstanceOf(ApiException.class);
        assertThat(org.unlock(manager,local,0).lockedUntil()).isNull();
        assertThat(auth.login("O"+tag+"L","OrgTest@123","test","127.0.0.1").response().user().id()).isEqualTo(local);
    }
    @Test void lastSuperAdminIsProtected() {
        assertThatThrownBy(()->org.saveEmployee(root,root,same(root,"DISABLED"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("LAST_SUPER_ADMIN"));
        assertThat(org.employee(root,root).status()).isEqualTo("ACTIVE");
    }
    @Test void concurrentSuperAdminDisableCannotRemoveBoth() throws Exception {
        long second=user(a,"ADMIN","ALL","S");jdbc.update("UPDATE sys_user SET super_admin=TRUE WHERE id=?",second);
        var firstBody=same(root,"DISABLED");var secondBody=same(second,"DISABLED");
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Boolean> first=()->{start.await();try{org.saveEmployee(root,root,firstBody);return true;}catch(ApiException e){return false;}};
            Callable<Boolean> other=()->{start.await();try{org.saveEmployee(second,second,secondBody);return true;}catch(ApiException e){return false;}};
            var f=pool.submit(first);var g=pool.submit(other);start.countDown();
            assertThat(List.of(f.get(10,TimeUnit.SECONDS),g.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE super_admin=TRUE AND status='ACTIVE' AND activated=TRUE",Integer.class)).isEqualTo(1);
        } finally {pool.shutdownNow();jdbc.update("UPDATE sys_user SET status='ACTIVE' WHERE id=?",root);jdbc.update("UPDATE sys_user SET super_admin=FALSE,status='DISABLED' WHERE id=?",second);}
    }
    @Test void scopedPlanPublicationRejectsForeignRosterWithoutPartialAssignments() {
        long plan=plan(manager);
        denied(()->plans.publish(plan,manager,List.of(local,foreign)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_assignment WHERE plan_id=?",Integer.class,plan)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM training_plan WHERE id=?",String.class,plan)).isEqualTo("DRAFT");
        assertThat(plans.publish(plan,manager,List.of(local,descendant)).plan().assigneeCount()).isEqualTo(2);
        long foreignPlan=plan(root);denied(()->plans.publish(foreignPlan,manager,List.of(local)));
        assertThat(plans.detail(foreignPlan,true,manager)).isEmpty();
        jdbc.update("UPDATE sys_user SET department_id=? WHERE id=?",b,descendant);
        assertThat(plans.detail(plan,true,manager).orElseThrow().plan().assigneeCount()).isEqualTo(1);
    }
    @Test void scopedExamPublishDoesNotAssignOtherDepartments() {
        long question=exams.createQuestion(new ExamDtos.QuestionRequest("single","Scope?",List.of("yes","no"),List.of(0),"Answer","QA","基础","ENABLED",0),manager);
        long paper=exams.createExam(new ExamDtos.ExamRequest("Scoped exam","QA",10,60,LocalDate.now().plusDays(1),List.of(new ExamDtos.ExamQuestionRequest(question,100))),manager);
        exams.publish(paper,manager);
        assertThat(jdbc.queryForList("SELECT user_id FROM exam_assignment WHERE paper_id=? ORDER BY user_id",Long.class,paper)).containsExactlyInAnyOrder(local,descendant);
        jdbc.update("INSERT INTO exam_attempt(paper_id,user_id,attempt_no) VALUES(?,?,1),(?,?,1)",paper,local,paper,foreign);
        assertThat(exams.exams(true,manager).stream().filter(e->e.id()==paper).findFirst().orElseThrow().attemptCount()).isEqualTo(1);
    }
    @Test void statisticsAndCertificatesOnlyExposeScopedPeople() {
        long plan=plan(root);plans.publish(plan,root,List.of(local,foreign));
        jdbc.update("UPDATE training_assignment SET status='COMPLETED',progress_percent=100 WHERE plan_id=?",plan);
        certificates.issue(plan,local);certificates.issue(plan,foreign);
        assertThat(certificates.admin(manager)).extracting(c->c.employeeNo()).containsExactly("O"+tag+"L");
        assertThat(archive.analytics(manager).activeEmployees()).isEqualTo(2);
        assertThat(archive.analytics(manager).planAssignments()).isEqualTo(1);
        assertThat(archive.analytics(manager).completedPlanAssignments()).isEqualTo(1);
    }
    @Test void httpAuthorizationAndAuditPersistRejectedAndSuccessfulWrites() throws Exception {
        String path="/api/v1/admin/employees/"+local+"/unlock";
        mvc.perform(post(path).with(actor(manager,"ADMIN")).with(csrf()).contentType("application/json").content("{\"version\":0}")).andExpect(status().isOk());
        mvc.perform(post(path).with(actor(manager,"ADMIN")).with(csrf()).contentType("application/json").content("{\"version\":0}")).andExpect(status().isConflict());
        mvc.perform(post(path).with(actor(foreign,"EMPLOYEE")).with(csrf()).contentType("application/json").content("{\"version\":1}")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("SELECT outcome FROM audit_log WHERE target=? ORDER BY id",String.class,path)).containsExactly("SUCCESS","FAILED","DENIED");
        assertThat(jdbc.queryForObject("SELECT actor_id FROM audit_log WHERE target=? AND outcome='SUCCESS'",Long.class,path)).isEqualTo(manager);
        mvc.perform(get("/api/v1/admin/audit-logs").with(actor(manager,"ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit-logs").with(actor(root,"ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/employees/{id}",foreign).with(actor(manager,"ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/employees?pageSize=99999").with(actor(root,"ADMIN"))).andExpect(status().isBadRequest());
    }
}
