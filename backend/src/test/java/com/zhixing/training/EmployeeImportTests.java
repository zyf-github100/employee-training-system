package com.zhixing.training;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.auth.*;
import com.zhixing.training.organization.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.zhixing.training.EmployeeWorkbookTests.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class EmployeeImportTests {
    @Autowired JdbcTemplate jdbc;@Autowired EmployeeImportService imports;@Autowired OrganizationService org;@Autowired AccountSetupService setup;
    @Autowired AuthService auth;@Autowired MockMvc mvc;@Autowired PasswordEncoder passwords;
    String tag,code,no;long root,department,manager;static String hash;
    @BeforeEach void fixture(){
        if(hash==null)hash=passwords.encode("ImportOld@123");tag=UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);code="IMP_"+tag;no="I"+tag;
        root=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no='ADMIN001'",Long.class);
        department=org.saveDepartment(root,null,new OrganizationDtos.DepartmentRequest(null,code,"Import department","ACTIVE",0,0));
        jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name,department_id,role_code,data_scope) VALUES(?,?,'Import manager',?,'ADMIN','DEPARTMENT')","M"+tag,hash,department);
        manager=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,"M"+tag);
    }
    EmployeeImportDtos.Preview preview(long actor,List<List<String>> rows)throws Exception{return imports.preview(actor,upload(sheet(rows)));}
    List<String> row(String number,String dept){return List.of(number,"待激活员工",dept,"工程师","qa@example.test","00123456");}
    long imported()throws Exception {var p=preview(manager,List.of(row(no,code)));imports.commit(manager,p.id());return id(no);}
    long id(String number){return jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,number);}
    AccountSetupService.Issued issue(long target){return setup.issue(manager,target,org.employee(root,target).version());}
    AccountSetupService.CompleteRequest completeBody(AccountSetupService.Issued token){return new AccountSetupService.CompleteRequest(token.employeeNo(),token.token(),"ImportNew@123");}
    @Test void previewDoesNotWriteAccountsAndCommitIsOwnedAtomicAndIdempotent()throws Exception {
        var p=preview(manager,List.of(row(no,code),row(no+"B",code)));assertThat(p.errorRows()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE employee_no LIKE ?",Integer.class,no+"%")).isZero();
        assertThatThrownBy(()->imports.commit(root,p.id())).isInstanceOf(ApiException.class);
        assertThat(imports.commit(manager,p.id()).status()).isEqualTo("COMMITTED");assertThat(imports.commit(manager,p.id()).totalRows()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee_import_user WHERE import_id=?",Integer.class,p.id())).isEqualTo(2);
        var employee=org.employee(root,id(no));assertThat(employee.activated()).isFalse();assertThat(employee.roleCode()).isEqualTo("EMPLOYEE");assertThat(employee.dataScope()).isEqualTo("SELF");
        assertThatThrownBy(()->auth.login(no,"ImportOld@123","test","127.0.0.1")).isInstanceOf(ApiException.class);
    }
    @Test void rowErrorsBlockAllRowsAndReportsCanBeCorrected()throws Exception {
        var p=preview(manager,List.of(row(no,code),row(no.toLowerCase(Locale.ROOT),code),List.of(no+"X","","TECH","","bad-mail","abc")));
        assertThat(p.errorRows()).isEqualTo(3);assertThat(p.rows().get(0).errors()).contains("文件内工号重复");
        assertThat(imports.commit(manager,p.id()).status()).isEqualTo("REVIEW_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE employee_no LIKE ?",Integer.class,no+"%")).isZero();
        try(var report=new XSSFWorkbook(new ByteArrayInputStream(imports.report(manager,p.id())))){assertThat(report.getSheetAt(0).getRow(1).getCell(6).getStringCellValue()).contains("重复");}
    }
    @Test void commitRevalidatesDepartmentScopeAndExistingNumbers()throws Exception {
        var p=preview(manager,List.of(row(no,code)));
        jdbc.update("UPDATE sys_user SET data_scope='SELF' WHERE id=?",manager);
        assertThat(imports.commit(manager,p.id()).errorRows()).isEqualTo(1);
        jdbc.update("UPDATE sys_user SET data_scope='DEPARTMENT' WHERE id=?",manager);
        var fresh=preview(manager,List.of(row(no,code)));
        jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name,department_id) VALUES(?,?,'Existing',?)",no,hash,department);
        assertThat(imports.commit(manager,fresh.id()).errorRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM sys_user WHERE employee_no=?",String.class,no)).isEqualTo("Existing");
    }
    @Test void expiredPreviewAndForeignOwnerCannotReadOrCommit()throws Exception {
        var p=preview(manager,List.of(row(no,code)));
        assertThatThrownBy(()->imports.read(root,p.id(),1,50)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("IMPORT_NOT_FOUND"));
        jdbc.update("UPDATE employee_import SET expires_at=TIMESTAMPADD(MINUTE,-1,CURRENT_TIMESTAMP) WHERE id=?",p.id());
        assertThatThrownBy(()->imports.commit(manager,p.id())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(410));
    }
    @Test void concurrentCommitCreatesEachEmployeeOnlyOnce()throws Exception {
        var p=preview(manager,List.of(row(no,code)));ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {Callable<String> task=()->{start.await();return imports.commit(manager,p.id()).status();};var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo("COMMITTED");assertThat(b.get(20,TimeUnit.SECONDS)).isEqualTo("COMMITTED");}
        finally{pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE employee_no=?",Integer.class,no)).isEqualTo(1);
    }
    @Test void fiveThousandRowsCommitWithinImportBudget()throws Exception {
        var rows=new ArrayList<List<String>>();for(int i=0;i<5000;i++)rows.add(row(no+String.format("%04d",i),code));
        Instant start=Instant.now();var p=preview(manager,rows);assertThat(p.totalRows()).isEqualTo(5000);assertThat(p.errorRows()).isZero();
        assertThat(imports.commit(manager,p.id()).status()).isEqualTo("COMMITTED");long millis=Duration.between(start,Instant.now()).toMillis();
        System.out.println("IMPORT_5000_ROWS_MILLIS="+millis);assertThat(millis).isLessThan(300000L);
        assertThat(imports.read(manager,p.id(),100,50).rows()).hasSize(50);
        Instant exportStart=Instant.now();
        assertThat(imports.activationList(manager,p.id(),"https://training.example.test")).isNotEmpty();
        long exportMillis=Duration.between(exportStart,Instant.now()).toMillis();
        System.out.println("ACTIVATION_5000_ROWS_MILLIS="+exportMillis);assertThat(exportMillis).isLessThan(300000L);
    }
    @Test void activationUsesHashedSingleUseTokenAndEmployeeChosenPassword()throws Exception {
        long target=imported();var token=issue(target);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM account_setup_token WHERE user_id=?",String.class,target)).isEqualTo(AuthService.hash(token.token())).isNotEqualTo(token.token());
        setup.complete(completeBody(token));assertThat(auth.login(no,"ImportNew@123","test","127.0.0.1").response().user().id()).isEqualTo(target);
        assertThatThrownBy(()->setup.complete(completeBody(token))).isInstanceOf(ApiException.class);
        assertThat(passwords.matches("ImportNew@123",jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=?",String.class,target))).isTrue();
    }
    @Test void reissuedExpiredAndChangedAccountsInvalidateOldLinks()throws Exception {
        long target=imported();var first=issue(target);var second=issue(target);
        assertThatThrownBy(()->setup.complete(completeBody(first))).isInstanceOf(ApiException.class);
        jdbc.update("UPDATE account_setup_token SET expires_at=TIMESTAMPADD(MINUTE,-1,CURRENT_TIMESTAMP) WHERE user_id=?",target);
        assertThatThrownBy(()->setup.complete(completeBody(second))).isInstanceOf(ApiException.class);
        var third=issue(target);jdbc.update("UPDATE sys_user SET version=version+1 WHERE id=?",target);
        assertThatThrownBy(()->setup.complete(completeBody(third))).isInstanceOf(ApiException.class);
        var fourth=issue(target);jdbc.update("UPDATE sys_user SET data_scope='SELF' WHERE id=?",manager);
        assertThatThrownBy(()->setup.complete(completeBody(fourth))).isInstanceOf(ApiException.class);
    }
    @Test void concurrentSetupConsumesTheTokenOnlyOnce()throws Exception {
        long target=imported();var token=issue(target);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Boolean> task=()->{start.await();try{setup.complete(completeBody(token));return true;}
                catch(ApiException e){assertThat(e.code()).isEqualTo("SETUP_LINK_INVALID");return false;}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        } finally{pool.shutdownNow();}
    }
    @Test void weakPasswordsAndManualActivationCannotBypassEmployeeSetup()throws Exception {
        long target=imported();var token=issue(target);var employee=org.employee(root,target);
        var request=new OrganizationDtos.EmployeeRequest(no,employee.name(),department,"工程师","qa@example.test","00123456",
            "EMPLOYEE",false,"SELF",List.of(),"ACTIVE",true,null,employee.version());
        assertThatThrownBy(()->org.saveEmployee(manager,target,request)).isInstanceOf(ApiException.class);
        for(String weak:List.of("a1","abcdefgh","12345678","密".repeat(25)+"a1"))
            assertThatThrownBy(()->setup.complete(new AccountSetupService.CompleteRequest(no,token.token(),weak))).isInstanceOf(ApiException.class);
        setup.complete(completeBody(token));assertThat(org.employee(root,target).activated()).isTrue();
    }
    @Test void malformedRequestsReturnClientErrorsWithoutExposingDetails()throws Exception {
        var admin=jwt().jwt(j->j.subject(""+manager)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(multipart("/api/v1/admin/employee-imports").with(admin).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/auth/account-setup").with(csrf()).contentType("application/json").content("{invalid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/admin/employee-imports/missing").param("pageNo","invalid").with(admin))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void resettingPasswordImmediatelyRevokesOldSessionsAndPassword()throws Exception {
        jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name,department_id) VALUES(?,?,'Active',?)",no,hash,department);long target=id(no);
        var login=auth.login(no,"ImportOld@123","test","127.0.0.1");var token=issue(target);assertThat(token.purpose()).isEqualTo("RESET");
        mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+login.response().accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->auth.login(no,"ImportOld@123","test","127.0.0.1")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->auth.refresh(login.refreshToken(),"test","127.0.0.1")).isInstanceOf(ApiException.class);
        setup.complete(completeBody(token));assertThat(auth.login(no,"ImportNew@123","test","127.0.0.1").response().user().id()).isEqualTo(target);
    }
    @Test void resetProtectsLastSuperAdminAndRejectsCrossDepartmentTargets()throws Exception {
        assertThatThrownBy(()->setup.issue(root,root,org.employee(root,root).version())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("LAST_SUPER_ADMIN"));
        assertThatThrownBy(()->setup.issue(manager,root,org.employee(root,root).version())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(403));
        jdbc.update("UPDATE sys_user SET super_admin=TRUE WHERE id=?",manager);
        try {assertThatThrownBy(()->setup.issue(root,root,org.employee(root,root).version())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("SELF_RESET_UNSUPPORTED"));}
        finally {jdbc.update("UPDATE sys_user SET super_admin=FALSE WHERE id=?",manager);}
    }
    @Test void activationListSkipsActiveUsersAndReissuesPendingTokens()throws Exception {
        var p=preview(manager,List.of(row(no,code),row(no+"B",code)));imports.commit(manager,p.id());var first=issue(id(no));setup.complete(completeBody(first));
        String before=jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=?",String.class,id(no));
        var bytes=imports.activationList(manager,p.id(),"https://training.example.test");
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){var sheet=book.getSheetAt(0);assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).contains("已激活");assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).startsWith("https://training.example.test/#/account-setup?");String token=sheet.getRow(2).getCell(4).getStringCellValue();setup.complete(new AccountSetupService.CompleteRequest(no+"B",token,"ImportNew@123"));}
        assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=?",String.class,id(no))).isEqualTo(before);
    }
    @Test void httpTemplateUploadAndSetupEnforceRolesCsrfAndDoNotCacheSecrets()throws Exception {
        var admin=jwt().jwt(j->j.subject(""+manager)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get("/api/v1/admin/employee-imports/template").with(admin)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(multipart("/api/v1/admin/employee-imports").file(upload(sheet(List.of(row(no,code))))).with(admin).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorRows").value(0));
        long target=imported();var employee=jwt().jwt(j->j.subject(""+target)).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        mvc.perform(get("/api/v1/admin/employee-imports/template").with(employee)).andExpect(status().isForbidden());
        var token=issue(target);String request="{\"employeeNo\":\""+no+"\",\"token\":\""+token.token()+"\",\"newPassword\":\"ImportNew@123\"}";
        mvc.perform(post("/api/v1/auth/account-setup").contentType("application/json").content(request)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/account-setup").with(csrf()).contentType("application/json").content(request)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_id=? AND target='/api/v1/auth/account-setup' AND outcome='SUCCESS'",Integer.class,target)).isEqualTo(1);
    }
}
