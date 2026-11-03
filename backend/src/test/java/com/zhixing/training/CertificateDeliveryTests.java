package com.zhixing.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.zhixing.training.api.ApiException;
import com.zhixing.training.certificate.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static com.zhixing.training.certificate.CertificateDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class CertificateDeliveryTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired CertificateRepository certificates;
    @Autowired CertificatePdfService pdf;
    @Autowired CertificateLinks links;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.core.env.Environment environment;
    long root,department,foreignDepartment,manager,employee,other,foreign,plan;
    String tag,originalIssuer;
    CertificateView certificate;
    @BeforeEach void fixture() throws Exception {
        Files.createDirectories(Path.of("target/certificate-qa"));
        tag=UUID.randomUUID().toString().substring(0,8);
        root=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no='ADMIN001'",Long.class);
        originalIssuer=jdbc.queryForObject("SELECT company_name FROM platform_setting WHERE id=1",String.class);
        department=department("A");foreignDepartment=department("B");
        manager=user(department,"ADMIN","DEPARTMENT","M");employee=user(department,"EMPLOYEE","SELF","E");other=user(department,"EMPLOYEE","SELF","O");foreign=user(foreignDepartment,"EMPLOYEE","SELF","F");
        plan=plan("企业安全与合规培训 "+tag);complete(employee);certificate=certificates.issue(plan,employee);
    }
    @AfterEach void restoreIssuer(){jdbc.update("UPDATE platform_setting SET company_name=?,version=version+1 WHERE id=1",originalIssuer);}
    long department(String suffix){String code="CERT_"+tag+suffix;jdbc.update("INSERT INTO org_department(code,name) VALUES(?,?)",code,"证书验收部门 "+suffix);return jdbc.queryForObject("SELECT id FROM org_department WHERE code=?",Long.class,code);}
    long user(long dept,String role,String scope,String suffix){String no="P"+tag+suffix;jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name,department_id,role_code,data_scope) VALUES(?,'unused',?,?,?,?)",no,"张晓宁"+suffix,dept,role,scope);return jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);}
    long plan(String title){jdbc.update("INSERT INTO training_plan(title,start_date,end_date,status,created_by) VALUES(?,CURRENT_DATE,DATE_ADD(CURRENT_DATE,INTERVAL 30 DAY),'PUBLISHED',?)",title,root);return jdbc.queryForObject("SELECT MAX(id) FROM training_plan WHERE title=?",Long.class,title);}
    void complete(long user){jdbc.update("INSERT INTO training_assignment(plan_id,user_id,status,progress_percent,completed_at) VALUES(?,?,'COMPLETED',100,DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 DAY))",plan,user);}
    RequestPostProcessor actor(long id,String role){return jwt().jwt(j->j.subject(""+id)).authorities(new SimpleGrantedAuthority("ROLE_"+role));}
    void denied(Runnable action){assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isIn(403,404));}
    void conflict(Runnable action){assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(409));}
    String text(byte[] bytes)throws Exception{try(var doc=Loader.loadPDF(bytes)){return new PDFTextStripper().getText(doc);}}
    @Test void certificateSnapshotsAndNumbersSurviveRenamesAndRepeatedIssuance() {
        String oldNo="ZHX-2020-"+plan+"-"+employee;jdbc.update("UPDATE training_certificate SET certificate_no=? WHERE id=?",oldNo,certificate.id());
        jdbc.update("UPDATE sys_user SET name='新姓名',employee_no=? WHERE id=?","RENAMED_"+tag,employee);
        jdbc.update("UPDATE training_plan SET title='新计划名称' WHERE id=?",plan);jdbc.update("UPDATE platform_setting SET company_name='新单位名称' WHERE id=1");
        var issued=certificates.issue(plan,employee);
        assertThat(issued.certificateNo()).isEqualTo(oldNo);assertThat(issued.recipientName()).isEqualTo(certificate.recipientName());
        assertThat(issued.employeeNo()).isEqualTo(certificate.employeeNo());assertThat(issued.planTitle()).isEqualTo(certificate.planTitle());assertThat(issued.issuerName()).isEqualTo(originalIssuer);
        assertThat(issued.verificationCode()).isEqualTo(certificate.verificationCode());assertThat(issued.issuedAt()).isEqualTo(certificate.issuedAt());assertThat(issued.completedAt()).isEqualTo(certificate.completedAt());assertThat(issued.legacySnapshot()).isFalse();
    }
    @Test void onlyCompletedAssignmentsCanIssueCertificates() {
        assertThatThrownBy(()->certificates.issue(plan,other)).isInstanceOf(ApiException.class);
    }
    @Test void v14UpgradePreservesHistoricalValidAndRevokedRecords() throws Exception {
        String schema="certificate_migration_"+UUID.randomUUID().toString().replace("-","");
        String source=environment.getRequiredProperty("spring.datasource.url");
        int slash=source.indexOf('/',"jdbc:mysql://".length()),query=source.indexOf('?');
        String url=source.substring(0,slash)+"/"+schema+(query<0?"":source.substring(query));
        String user=environment.getRequiredProperty("spring.datasource.username"),password=environment.getProperty("spring.datasource.password","");
        jdbc.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4");
        try {
            org.flywaydb.core.Flyway.configure().dataSource(url,user,password).target("14").load().migrate();
            var old=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,user,password));
            old.update("INSERT INTO org_department(id,code,name) VALUES(1,'LEGACY','原部门')");
            old.update("INSERT INTO sys_user(id,employee_no,password_hash,name,department_id,role_code) VALUES(1,'LEGACY001','unused','历史学员',1,'EMPLOYEE'),(2,'LEGACY002','unused','撤销学员',1,'EMPLOYEE')");
            old.update("INSERT INTO training_plan(id,title,start_date,end_date,status,created_by) VALUES(1,'历史培训',CURRENT_DATE,CURRENT_DATE,'PUBLISHED',1)");
            old.update("UPDATE platform_setting SET company_name='历史签发单位' WHERE id=1");
            old.update("INSERT INTO training_certificate(certificate_no,plan_id,user_id,title,issued_at,revoked_at) VALUES('OLD-VALID',1,1,'历史证书','2020-01-02',NULL),('OLD-REVOKED',1,2,'已撤销历史证书','2020-01-02','2020-02-01')");
            org.flywaydb.core.Flyway.configure().dataSource(url,user,password).load().migrate();
            var records=old.queryForList("SELECT * FROM training_certificate ORDER BY id");
            assertThat(records).hasSize(2);
            assertThat(records.get(0)).containsEntry("certificate_no","OLD-VALID").containsEntry("recipient_name_snapshot","历史学员").containsEntry("employee_no_snapshot","LEGACY001").containsEntry("plan_title_snapshot","历史培训").containsEntry("issuer_name","历史签发单位").containsEntry("snapshot_origin","LEGACY");
            assertThat(records.get(0).get("completed_at_snapshot")).isEqualTo(records.get(0).get("issued_at"));
            assertThat(records.get(0).get("revoked_at")).isNull();assertThat(records.get(1).get("revoked_at")).isNotNull();
            assertThat(records).extracting(r->(String)r.get("verification_code")).allMatch(c->c.matches("[a-f0-9]{64}")).doesNotHaveDuplicates();
            assertThatThrownBy(()->old.update("DELETE FROM sys_user WHERE id=2")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally { jdbc.execute("DROP DATABASE "+schema); }
    }
    @Test void pdfEmbedsChineseFontAndContainsOneScannableVerificationQr() throws Exception {
        byte[] bytes=pdf.render(certificate);Path target=Path.of("target/certificate-qa");Files.createDirectories(target);Files.write(target.resolve("valid.pdf"),bytes);
        try(var doc=Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);assertThat(doc.getPage(0).getMediaBox().getWidth()).isGreaterThan(doc.getPage(0).getMediaBox().getHeight());
            assertThat(new PDFTextStripper().getText(doc)).contains(certificate.recipientName(),certificate.employeeNo(),certificate.planTitle(),certificate.issuerName(),certificate.certificateNo());
            for(var fontName:doc.getPage(0).getResources().getFontNames()){var font=doc.getPage(0).getResources().getFont(fontName);assertThat(font).isInstanceOf(PDType0Font.class);assertThat(font.getFontDescriptor().getFontFile2()).isNotNull();}
            var image=new PDFRenderer(doc).renderImageWithDPI(0,160);javax.imageio.ImageIO.write(image,"png",target.resolve("valid.png").toFile());
            var bitmap=new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()))));
            var decoded=new MultiFormatReader().decode(bitmap,Map.of(DecodeHintType.TRY_HARDER,true));assertThat(decoded.getText()).isEqualTo(links.verification(certificate.verificationCode()));
            assertThat(doc.getPage(0).getAnnotations()).hasSize(1);
        }
    }
    @Test void longChineseNamesTitlesAndIssuersRemainCompleteOnOnePage() throws Exception {
        String name="长姓名测试".repeat(15),title="长标题培训课程".repeat(20),issuer="长签发单位".repeat(15);
        jdbc.update("UPDATE training_certificate SET recipient_name_snapshot=?,plan_title_snapshot=?,issuer_name=? WHERE id=?",name,title,issuer,certificate.id());
        var bytes=pdf.render(certificates.authorized(root,true,certificate.id()));
        try(var doc=Loader.loadPDF(bytes)){assertThat(doc.getNumberOfPages()).isEqualTo(1);String content=new PDFTextStripper().getText(doc).replaceAll("\\s","");assertThat(content).contains(name,title,issuer);var image=new PDFRenderer(doc).renderImageWithDPI(0,120);javax.imageio.ImageIO.write(image,"png",Path.of("target/certificate-qa/long.png").toFile());}
    }
    @Test void publicVerificationNeedsSecretCodeAndExposesOnlyVerificationFields() throws Exception {
        mvc.perform(get("/api/v1/certificates/verify").param("code",certificate.verificationCode())).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.data.status").value("VALID")).andExpect(jsonPath("$.data.recipientName").value(certificate.recipientName())).andExpect(jsonPath("$.data.employeeNoMasked").value("****"+certificate.employeeNo().substring(certificate.employeeNo().length()-4)))
                .andExpect(jsonPath("$.data.employeeNo").doesNotExist()).andExpect(jsonPath("$.data.verificationCode").doesNotExist()).andExpect(jsonPath("$.data.revocationReason").doesNotExist()).andExpect(jsonPath("$.data.id").doesNotExist());
        mvc.perform(get("/api/v1/certificates/verify").param("code",certificate.certificateNo())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/certificates/verify").param("code","a".repeat(64))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/certificates/verify").param("code","<script>")).andExpect(status().isNotFound());
    }
    @Test void pdfEndpointsEnforceOwnerAndCurrentDepartmentScope() throws Exception {
        String path="/api/v1/employee/certificates/"+certificate.id()+"/pdf";
        mvc.perform(get(path).with(actor(employee,"EMPLOYEE"))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString(".pdf")));
        mvc.perform(get(path).with(actor(other,"EMPLOYEE"))).andExpect(status().isNotFound());mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/certificates/{id}/pdf",certificate.id()).with(actor(manager,"ADMIN"))).andExpect(status().isOk());
        jdbc.update("UPDATE sys_user SET data_scope='SELF' WHERE id=?",manager);
        mvc.perform(get("/api/v1/admin/certificates/{id}/pdf",certificate.id()).with(actor(manager,"ADMIN"))).andExpect(status().isNotFound());
    }
    @Test void certificateSearchIsScopedAndUsesSnapshotFieldsAndStatus() {
        complete(foreign);certificates.issue(plan,foreign);
        assertThat(certificates.page(manager,true,1,20,tag,"VALID").total()).isEqualTo(1);
        assertThat(certificates.page(employee,false,1,20,certificate.certificateNo(),"").list()).extracting(CertificateView::id).containsExactly(certificate.id());
        certificates.revoke(manager,certificate.id(),new RevokeRequest("撤销测试",0));
        assertThat(certificates.page(manager,true,1,20,tag,"VALID").total()).isZero();assertThat(certificates.page(manager,true,1,20,tag,"REVOKED").total()).isEqualTo(1);
    }
    @Test void revocationIsVersionedIdempotentAndVisibleToVerifierAndOwner() throws Exception {
        conflict(()->certificates.revoke(manager,certificate.id(),new RevokeRequest("过期请求",9)));
        var revoked=certificates.revoke(manager,certificate.id(),new RevokeRequest("签发信息复核不通过",0));
        var again=certificates.revoke(root,certificate.id(),new RevokeRequest("签发信息复核不通过",0));assertThat(again.revokedAt()).isEqualTo(revoked.revokedAt());assertThat(again.version()).isEqualTo(1);assertThat(again.revokedByName()).isEqualTo("张晓宁M");
        conflict(()->certificates.revoke(root,certificate.id(),new RevokeRequest("不能覆盖原因",1)));
        assertThat(certificates.employee(employee).get(0).revocationReason()).isEqualTo("签发信息复核不通过");
        mvc.perform(get("/api/v1/certificates/verify").param("code",certificate.verificationCode())).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED")).andExpect(jsonPath("$.data.revocationReason").doesNotExist());
        byte[] bytes=pdf.render(revoked);assertThat(text(bytes)).contains("已撤销","本证书当前无效");Files.write(Path.of("target/certificate-qa/revoked.pdf"),bytes);
        try(var doc=Loader.loadPDF(bytes)){javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,120),"png",Path.of("target/certificate-qa/revoked.png").toFile());}
    }
    @Test void automaticCompletionCannotReactivateARevokedCertificate() {
        var revoked=certificates.revoke(manager,certificate.id(),new RevokeRequest("撤销后不得自动重新生效",0));
        var reissued=certificates.issue(plan,employee);assertThat(reissued.status()).isEqualTo("REVOKED");assertThat(reissued.id()).isEqualTo(revoked.id());assertThat(reissued.verificationCode()).isEqualTo(revoked.verificationCode());assertThat(reissued.version()).isEqualTo(1);
    }
    @Test void foreignManagersAndEmployeesCannotRevokeAndReasonsAreRequired() throws Exception {
        jdbc.update("UPDATE sys_user SET department_id=? WHERE id=?",foreignDepartment,manager);
        denied(()->certificates.revoke(manager,certificate.id(),new RevokeRequest("不应允许",0)));
        String path="/api/v1/admin/certificates/"+certificate.id()+"/revoke";
        mvc.perform(post(path).with(actor(employee,"EMPLOYEE")).with(csrf()).contentType("application/json").content("{\"reason\":\"不应允许\",\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(post(path).with(actor(root,"ADMIN")).with(csrf()).contentType("application/json").content("{\"reason\":\" \",\"version\":0}")).andExpect(status().isBadRequest());
        assertThat(certificates.authorized(root,true,certificate.id()).status()).isEqualTo("VALID");
    }
    @Test void concurrentRevocationsPreserveOneCauseAndOneVersion() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        try {Callable<Boolean> first=()->{go.await();try{certificates.revoke(root,certificate.id(),new RevokeRequest("原因甲",0));return true;}catch(ApiException e){return false;}};
            Callable<Boolean> second=()->{go.await();try{certificates.revoke(manager,certificate.id(),new RevokeRequest("原因乙",0));return true;}catch(ApiException e){return false;}};
            var a=pool.submit(first);var b=pool.submit(second);go.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(certificates.authorized(root,true,certificate.id()).version()).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
    @Test void managementDownloadsAndRevocationsProduceAudits() throws Exception {
        String path="/api/v1/admin/certificates/"+certificate.id();
        mvc.perform(get(path+"/pdf").with(actor(manager,"ADMIN"))).andExpect(status().isOk());
        mvc.perform(post(path+"/revoke").with(actor(manager,"ADMIN")).with(csrf()).contentType("application/json").content("{\"reason\":\"审计验收\",\"version\":0}")).andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT outcome FROM audit_log WHERE target IN (?,?) ORDER BY id",String.class,path+"/pdf",path+"/revoke")).containsExactly("SUCCESS","SUCCESS");
    }
    @Test void unsupportedCharactersFailClearlyInsteadOfChangingTheRecipient() {
        jdbc.update("UPDATE training_certificate SET recipient_name_snapshot=? WHERE id=?",new String(Character.toChars(0x10ffff)),certificate.id());
        assertThatThrownBy(()->pdf.render(certificates.authorized(root,true,certificate.id()))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("CERTIFICATE_FONT_UNSUPPORTED"));
    }
}
