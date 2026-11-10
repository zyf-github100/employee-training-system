package com.zhixing.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.training.api.ApiException;
import com.zhixing.training.auth.*;
import com.zhixing.training.organization.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static com.zhixing.training.organization.OrganizationDtos.*;
import static com.zhixing.training.organization.OrganizationBatchDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class OrganizationBatchTests {
    @Autowired JdbcTemplate jdbc; @Autowired OrganizationService org; @Autowired OrganizationBatchService batch;
    @Autowired AuthService auth; @Autowired AccountSetupService setup; @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    long root,a,child,b,manager,one,two,outside; String tag; static String hash;
    @BeforeEach void fixture() {
        if(hash==null) hash=encoder.encode("BatchTest@123");
        tag=UUID.randomUUID().toString().substring(0,8);
        root=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no='ADMIN001'",Long.class);
        a=department(null,"A");child=department(a,"C");b=department(null,"B");
        manager=user(a,"ADMIN","M");one=user(a,"EMPLOYEE","1");two=user(a,"EMPLOYEE","2");outside=user(b,"EMPLOYEE","X");
    }
    long department(Long parent,String suffix) {return org.saveDepartment(root,null,new DepartmentRequest(parent,"B_"+tag+suffix,"Batch "+suffix,"ACTIVE",0,0));}
    long user(long department,String role,String suffix) {
        String no="B"+tag+suffix;
        jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name,department_id,role_code,data_scope,job_title) VALUES(?,?,?,?,?,?,'Original job')",
                no,hash,"Batch "+suffix,department,role,"ADMIN".equals(role)?"DEPARTMENT_TREE":"SELF");
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);
    }
    List<Target> selected(long... ids) {return Arrays.stream(ids).mapToObj(id->new Target(id,org.employee(root,id).version())).toList();}
    TransferRequest transfer(long department,long... ids) {return new TransferRequest(UUID.randomUUID().toString(),selected(ids),department,false,null);}
    RoleRequest roles(String role,String scope,List<Long> custom,long... ids) {return new RoleRequest(UUID.randomUUID().toString(),selected(ids),role,scope,custom);}
    Department dept(long id) {return org.departments(root).stream().filter(d->d.id()==id).findFirst().orElseThrow();}
    void denied(Runnable action) {assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(403));}
    void disable(long id) {
        var e=org.employee(root,id);
        org.saveEmployee(root,id,new EmployeeRequest(e.employeeNo(),e.name(),e.departmentId(),e.jobTitle(),e.email(),e.phone(),e.roleCode(),e.superAdmin(),e.dataScope(),e.customDepartmentIds(),"DISABLED",e.activated(),null,e.version()));
    }
    @Test void transfersKeepIdentityAndCanPreserveOrClearJobTitles() {
        var result=batch.transfer(manager,transfer(child,one,two));assertThat(result.changed()).isEqualTo(2);
        var e=org.employee(root,one);assertThat(e.departmentId()).isEqualTo(child);assertThat(e.employeeNo()).isEqualTo("B"+tag+"1");assertThat(e.jobTitle()).isEqualTo("Original job");
        var same=transfer(child,one);assertThat(batch.transfer(root,same).changed()).isZero();assertThat(org.employee(root,one).version()).isEqualTo(e.version());
        var clear=new TransferRequest(UUID.randomUUID().toString(),selected(one),child,true,"  ");
        assertThat(batch.transfer(root,clear).changed()).isEqualTo(1);assertThat(org.employee(root,one).jobTitle()).isEmpty();
    }
    @Test void validatesAllSourcesAndDestinationBeforeAnyTransfer() {
        denied(()->batch.transfer(manager,transfer(child,one,outside)));
        denied(()->batch.transfer(manager,transfer(b,one,two)));
        assertThatThrownBy(()->batch.transfer(manager,transfer(child,one,manager))).isInstanceOf(ApiException.class);
        assertThat(org.employee(root,one).departmentId()).isEqualTo(a);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization_batch WHERE actor_id=?",Integer.class,manager)).isZero();
    }
    @Test void staleVersionRollsBackWholeBatchAndDuplicateTargetsAreRejected() {
        var request=transfer(child,one,two);jdbc.update("UPDATE sys_user SET version=version+1 WHERE id=?",two);
        assertThatThrownBy(()->batch.transfer(root,request)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("VERSION_CONFLICT"));
        assertThat(org.employee(root,one).version()).isZero();assertThat(org.employee(root,one).departmentId()).isEqualTo(a);
        var duplicate=new TransferRequest(UUID.randomUUID().toString(),List.of(new Target(one,0),new Target(one,0)),child,false,null);
        assertThatThrownBy(()->batch.transfer(root,duplicate)).isInstanceOf(ApiException.class);
    }
    @Test void replaysAreOwnedAndBoundToExactPayload() {
        var request=transfer(child,one,two);var result=batch.transfer(root,request);
        assertThat(batch.transfer(root,request)).isEqualTo(result);assertThat(org.employee(root,one).version()).isEqualTo(1);
        var reordered=new TransferRequest(request.requestId(),List.of(request.targets().get(1),request.targets().get(0)),child,false,"");
        assertThat(batch.transfer(root,reordered)).isEqualTo(result);
        var altered=new TransferRequest(request.requestId(),request.targets(),b,false,null);
        assertThatThrownBy(()->batch.transfer(root,altered)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("BATCH_REQUEST_CONFLICT"));
        assertThatThrownBy(()->batch.transfer(manager,request)).isInstanceOf(ApiException.class);
    }
    @Test void batchHistoryStoresSnapshotsAndRollbackRefusesStaleTargets() {
        var request=transfer(child,one);batch.transfer(root,request);
        var page=batch.history(root,1,20,"TRANSFER");assertThat(page.total()).isGreaterThanOrEqualTo(1);
        var current=page.list().stream().filter(item->item.requestId().equals(request.requestId())).findFirst().orElseThrow();
        assertThat(current.before()).hasSize(1);assertThat(current.after()).hasSize(1);
        var rolled=batch.rollback(root,request.requestId(),new RollbackRequest("恢复原部门"));
        assertThat(rolled.rolledBackAt()).isNotNull();assertThat(org.employee(root,one).departmentId()).isEqualTo(a);
        assertThat(batch.rollback(root,request.requestId(),new RollbackRequest("重复恢复")).rolledBackAt()).isEqualTo(rolled.rolledBackAt());
        var second=transfer(child,one);batch.transfer(root,second);
        jdbc.update("UPDATE sys_user SET job_title='Changed elsewhere',version=version+1 WHERE id=?",one);
        assertThatThrownBy(()->batch.rollback(root,second.requestId(),new RollbackRequest("过期恢复"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("ROLLBACK_CONFLICT"));
    }
    @Test void concurrentRetryCommitsOneBatchAndOneVersionIncrement() throws Exception {
        var request=transfer(child,one,two);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Result> call=()->{start.await();return batch.transfer(root,request);};
            var first=pool.submit(call);var second=pool.submit(call);start.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(second.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertThat(org.employee(root,one).version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization_batch WHERE request_id=?",Integer.class,request.requestId())).isEqualTo(1);
    }
    @Test void onlySuperAdminCanAssignRolesAndSuperAccountsAreExcluded() {
        denied(()->batch.roles(manager,roles("ADMIN","ALL",List.of(),one)));
        assertThatThrownBy(()->batch.roles(root,roles("EMPLOYEE","SELF",List.of(),one,root))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->batch.transfer(root,transfer(child,one,root))).isInstanceOf(ApiException.class);
        assertThat(org.employee(root,root).superAdmin()).isTrue();assertThat(org.employee(root,one).version()).isZero();
        var promote=roles("ADMIN","CUSTOM",List.of(b,child,b),one,two);
        assertThat(batch.roles(root,promote).changed()).isEqualTo(2);
        assertThat(org.employee(root,one).superAdmin()).isFalse();assertThat(org.employee(root,one).customDepartmentIds()).containsExactly(child,b);
        assertThat(batch.roles(root,roles("EMPLOYEE","SELF",List.of(),one,two)).changed()).isEqualTo(2);
        assertThat(org.employee(root,one).customDepartmentIds()).isEmpty();
    }
    @Test void invalidRoleScopeOrDisabledDepartmentsNeverPartiallyApply() {
        assertThatThrownBy(()->batch.roles(root,roles("EMPLOYEE","ALL",List.of(),one))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->batch.roles(root,roles("ADMIN","CUSTOM",List.of(),one))).isInstanceOf(ApiException.class);
        long disabled=department(null,"Z");jdbc.update("UPDATE org_department SET status='DISABLED' WHERE id=?",disabled);
        assertThatThrownBy(()->batch.transfer(root,transfer(disabled,one,two))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->batch.roles(root,roles("ADMIN","CUSTOM",List.of(a,disabled),one,two))).isInstanceOf(ApiException.class);
        assertThat(org.employee(root,one).version()).isZero();
    }
    @Test void transferAndRoleChangeRevokeRealSessionsAndPendingSetupLinks() throws Exception {
        var old=auth.login("B"+tag+"1","BatchTest@123","test","127.0.0.1");
        batch.transfer(root,transfer(child,one));
        mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+old.response().accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->auth.refresh(old.refreshToken(),"test","127.0.0.1")).isInstanceOf(ApiException.class);
        var newer=auth.login("B"+tag+"1","BatchTest@123","test","127.0.0.1");batch.roles(root,roles("ADMIN","DEPARTMENT",List.of(),one));
        mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+newer.response().accessToken())).andExpect(status().isUnauthorized());
        var token=setup.issue(root,two,org.employee(root,two).version());batch.transfer(root,transfer(child,two));
        assertThatThrownBy(()->setup.complete(new AccountSetupService.CompleteRequest(token.employeeNo(),token.token(),"Changed@123"))).isInstanceOf(ApiException.class);
    }
    @Test void firstLoginMustChangePasswordBeforeBusinessApis() throws Exception {
        String no="FIRST"+tag; jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name,must_change_password) VALUES(?,?,?,TRUE)",no,hash,"First login");
        var issued=auth.login(no,"BatchTest@123","test","127.0.0.1");assertThat(issued.response().user().mustChangePassword()).isTrue();
        mvc.perform(get("/api/v1/employee/courses").header("Authorization","Bearer "+issued.response().accessToken())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+issued.response().accessToken())).andExpect(status().isOk()).andExpect(jsonPath("$.data.mustChangePassword").value(true));
        mvc.perform(put("/api/v1/me/password").with(csrf()).header("Authorization","Bearer "+issued.response().accessToken()).contentType("application/json").content("{\"currentPassword\":\"BatchTest@123\",\"newPassword\":\"Changed@123\"}"))
                .andExpect(status().isOk());
        var next=auth.login(no,"Changed@123","test","127.0.0.1");assertThat(next.response().user().mustChangePassword()).isFalse();
    }
    @Test void leadershipIsScopedVersionedAndDoesNotGrantPermissions() {
        denied(()->org.setLeader(manager,a,new LeaderRequest(one,0)));
        assertThatThrownBy(()->org.setLeader(root,a,new LeaderRequest(outside,0))).isInstanceOf(ApiException.class);
        org.setLeader(root,a,new LeaderRequest(one,0));assertThat(dept(a).leaderUserId()).isEqualTo(one);
        denied(()->org.access(one));assertThat(org.employee(root,one).roleCode()).isEqualTo("EMPLOYEE");
        assertThatThrownBy(()->org.setLeader(root,a,new LeaderRequest(two,0))).isInstanceOf(ApiException.class);
        org.setLeader(root,a,new LeaderRequest(null,dept(a).version()));assertThat(dept(a).leaderUserId()).isNull();
        disable(two);assertThatThrownBy(()->org.setLeader(root,a,new LeaderRequest(two,dept(a).version()))).isInstanceOf(ApiException.class);
    }
    @Test void movingOrDisablingLeaderClearsAppointmentAndInvalidatesDepartmentEditor() {
        org.setLeader(root,a,new LeaderRequest(one,0));long version=dept(a).version();
        assertThat(batch.transfer(root,transfer(child,one)).clearedLeaders()).isEqualTo(1);
        assertThat(dept(a).leaderUserId()).isNull();assertThat(dept(a).version()).isEqualTo(version+1);
        assertThatThrownBy(()->org.setLeader(root,a,new LeaderRequest(two,version))).isInstanceOf(ApiException.class);
        org.setLeader(root,a,new LeaderRequest(two,dept(a).version()));disable(two);assertThat(dept(a).leaderUserId()).isNull();
    }
    @Test void concurrentAppointmentAndTransferNeverLeaveForeignLeader() throws Exception {
        var request=transfer(child,one);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var appointment=pool.submit(()->{start.await();try{org.setLeader(root,a,new LeaderRequest(one,0));}catch(ApiException expected){}return true;});
            var move=pool.submit(()->{start.await();return batch.transfer(root,request);});start.countDown();
            appointment.get(20,TimeUnit.SECONDS);move.get(20,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertThat(dept(a).leaderUserId()).isNull();assertThat(org.employee(root,one).departmentId()).isEqualTo(child);
    }
    @Test void twoHundredAccountsCanBeTransferredInOneBatch() {
        long[] ids=new long[200];for(int i=0;i<ids.length;i++)ids[i]=user(a,"EMPLOYEE","P"+i);
        assertThat(batch.transfer(manager,transfer(child,ids)).changed()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE department_id=?",Integer.class,child)).isEqualTo(200);
    }
    @Test void httpValidatesBatchLimitRolesAndAuditsResults() throws Exception {
        var admin=jwt().jwt(j->j.subject(""+root)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        String path="/api/v1/admin/employees/batch/transfer";
        var request=transfer(child,one,two);
        var employee=jwt().jwt(j->j.subject(""+one)).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        mvc.perform(post(path).with(employee).with(csrf()).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(admin).with(csrf()).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(2));
        var huge=new TransferRequest(UUID.randomUUID().toString(),Collections.nCopies(201,new Target(one,1)),child,false,null);
        mvc.perform(post(path).with(admin).with(csrf()).contentType("application/json").content(json.writeValueAsString(huge))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE target=? AND actor_id=? AND outcome='SUCCESS'",Integer.class,path,root)).isGreaterThanOrEqualTo(1);
    }
}
