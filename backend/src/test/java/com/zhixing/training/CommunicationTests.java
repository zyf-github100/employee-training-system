package com.zhixing.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.training.api.ApiException;
import com.zhixing.training.communication.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.*;
import java.util.concurrent.*;
import static com.zhixing.training.communication.CommunicationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class CommunicationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired FeedbackService feedback;
    @Autowired AnnouncementService notices;
    @Autowired PlatformService platform;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    long root,dept,child,foreignDept,manager,otherManager,employee,descendant,foreign;
    String tag;
    PlatformView original;
    @BeforeEach void fixture() {
        tag=UUID.randomUUID().toString().substring(0,8);original=platform.read();
        root=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no='ADMIN001'",Long.class);
        dept=department(null,"A");child=department(dept,"C");foreignDept=department(null,"B");
        manager=user(dept,"ADMIN","DEPARTMENT_TREE","M");otherManager=user(foreignDept,"ADMIN","DEPARTMENT","N");
        employee=user(dept,"EMPLOYEE","SELF","E");descendant=user(child,"EMPLOYEE","SELF","D");foreign=user(foreignDept,"EMPLOYEE","SELF","F");
    }
    @AfterEach void restoreSettings() {
        jdbc.update("UPDATE platform_setting SET company_name=?,contact_name=?,contact_email=?,unread_reminders=?,version=version+1 WHERE id=1",original.companyName(),original.contactName(),original.contactEmail(),original.unreadReminders());
    }
    long department(Long parent,String suffix) {
        String code="COM_"+tag+suffix;
        jdbc.update("INSERT INTO org_department(code,name,parent_id) VALUES(?,?,?)",code,"Communication "+suffix,parent);
        return jdbc.queryForObject("SELECT id FROM org_department WHERE code=?",Long.class,code);
    }
    long user(long department,String role,String scope,String suffix) {
        String no="C"+tag+suffix;
        jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name,department_id,role_code,data_scope) VALUES(?,'unused',?,?,?,?)",no,"User "+suffix,department,role,scope);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);
    }
    RequestPostProcessor actor(long id,String role){return jwt().jwt(j->j.subject(""+id)).authorities(new SimpleGrantedAuthority("ROLE_"+role));}
    FeedbackRequest question(){return new FeedbackRequest(UUID.randomUUID(),"Feedback "+tag,"课程提问","How to complete training?");}
    AnnouncementRequest announcement(List<Long> ids){return new AnnouncementRequest(UUID.randomUUID(),"Notice "+tag,"培训通知","Training begins next week.",ids);}
    void denied(Runnable action){assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(403));}
    void conflict(Runnable action){assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(409));}
    @Test void feedbackIdentityIsServerOwnedAndRequestsAreIdempotent() throws Exception {
        var request=question();
        String body=json.writeValueAsString(request).replace("}",",\"userId\":"+foreign+"}");
        var response=mvc.perform(post("/api/v1/employee/feedback").with(actor(employee,"EMPLOYEE")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(employee)).andReturn();
        long id=json.readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertThat(feedback.create(employee,request).id()).isEqualTo(id);
        conflict(()->feedback.create(employee,new FeedbackRequest(request.requestId(),"Different","课程提问",request.content())));
        assertThat(feedback.list(employee,false,1,20,"",tag).total()).isEqualTo(1);
        assertThat(feedback.list(foreign,false,1,20,"",tag).total()).isZero();
    }
    @Test void repliesRespectScopeAndRejectLostUpdates() throws Exception {
        var local=feedback.create(employee,question());feedback.create(foreign,question());
        assertThat(feedback.list(manager,true,1,20,"PENDING",tag).list()).extracting(FeedbackView::id).containsExactly(local.id());
        var reply=feedback.reply(manager,local.id(),new ReplyRequest("Answer",0));
        assertThat(reply.status()).isEqualTo("REPLIED");assertThat(reply.repliedByName()).isEqualTo("User M");
        assertThat(feedback.list(employee,false,1,20,"REPLIED",tag).list().get(0).reply()).isEqualTo("Answer");
        conflict(()->feedback.reply(root,local.id(),new ReplyRequest("Stale",0)));
        denied(()->feedback.reply(otherManager,local.id(),new ReplyRequest("Outside scope",1)));
        mvc.perform(put("/api/v1/admin/feedback/{id}/reply",local.id()).with(actor(employee,"EMPLOYEE")).with(csrf()).contentType("application/json").content("{\"reply\":\"Forbidden\",\"version\":1}")).andExpect(status().isForbidden());
    }
    @Test void permissionChangesApplyToExistingFeedbackImmediately() {
        var local=feedback.create(descendant,question());
        assertThat(feedback.list(manager,true,1,20,"",tag).total()).isEqualTo(1);
        jdbc.update("UPDATE sys_user SET data_scope='DEPARTMENT' WHERE id=?",manager);
        assertThat(feedback.list(manager,true,1,20,"",tag).total()).isZero();
        denied(()->feedback.reply(manager,local.id(),new ReplyRequest("No longer authorized",0)));
        jdbc.update("UPDATE sys_user SET data_scope='CUSTOM' WHERE id=?",manager);
        jdbc.update("INSERT INTO user_scope_department(user_id,department_id) VALUES(?,?)",manager,child);
        assertThat(feedback.list(manager,true,1,20,"",tag).total()).isEqualTo(1);
        jdbc.update("UPDATE sys_user SET data_scope='SELF' WHERE id=?",manager);
        assertThat(feedback.list(manager,true,1,20,"",tag).total()).isZero();
    }
    @Test void feedbackPagesAndFiltersHaveAccurateTotals() {
        for(int i=0;i<21;i++)feedback.create(employee,question());
        assertThat(feedback.list(manager,true,1,20,"PENDING",tag).list()).hasSize(20);
        var second=feedback.list(manager,true,2,20,"PENDING",tag);assertThat(second.total()).isEqualTo(21);assertThat(second.list()).hasSize(1);
        feedback.reply(manager,second.list().get(0).id(),new ReplyRequest("Done",0));
        assertThat(feedback.list(manager,true,1,20,"REPLIED",tag).total()).isEqualTo(1);
    }
    @Test void invalidFeedbackAndPaginationAreRejectedOverHttp() throws Exception {
        mvc.perform(post("/api/v1/employee/feedback").with(actor(employee,"EMPLOYEE")).with(csrf()).contentType("application/json").content("{\"requestId\":\""+UUID.randomUUID()+"\",\"title\":\" \",\"type\":\"other\",\"content\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/feedback?pageNo=0").with(actor(manager,"ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/employee/feedback")).andExpect(status().isUnauthorized());
    }
    @Test void publicationRejectsAnEntireCrossScopeOrInactiveRoster() {
        var request=announcement(List.of(employee,foreign));denied(()->notices.publish(manager,request));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_announcement WHERE request_id=?",Integer.class,request.requestId().toString())).isZero();
        jdbc.update("UPDATE sys_user SET status='DISABLED' WHERE id=?",descendant);
        denied(()->notices.publish(manager,announcement(List.of(employee,descendant))));
        denied(()->notices.publish(manager,announcement(List.of(manager))));
    }
    @Test void announcementsFreezeDeduplicatedRecipientsAndKeepScopedCounts() {
        var request=announcement(List.of(employee,descendant,employee));var view=notices.publish(manager,request);
        assertThat(view.recipientCount()).isEqualTo(2);assertThat(notices.publish(manager,request).id()).isEqualTo(view.id());
        conflict(()->notices.publish(manager,new AnnouncementRequest(request.requestId(),request.title(),request.category(),request.content(),List.of(employee))));
        long later=user(dept,"EMPLOYEE","SELF","Z");assertThat(notices.list(later,false,1,20).total()).isZero();
        assertThat(notices.list(foreign,false,1,20).list()).noneMatch(n->n.id()==view.id());
        assertThat(notices.list(otherManager,true,1,20).list()).noneMatch(n->n.id()==view.id());
        jdbc.update("UPDATE sys_user SET data_scope='DEPARTMENT' WHERE id=?",manager);
        assertThat(notices.list(manager,true,1,20).list().get(0).recipientCount()).isEqualTo(1);
    }
    @Test void readingIsPrivateIdempotentAndWithdrawalRemovesUnreadItems() {
        var view=notices.publish(manager,announcement(List.of(employee,descendant)));
        assertThat(notices.unread(employee)).isEqualTo(1);
        var read=notices.read(employee,view.id());assertThat(read.readAt()).isNotNull();
        assertThat(notices.read(employee,view.id()).readAt()).isEqualTo(read.readAt());assertThat(notices.unread(employee)).isZero();
        assertThat(notices.unread(descendant)).isEqualTo(1);
        assertThatThrownBy(()->notices.read(foreign,view.id())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(404));
        denied(()->notices.withdraw(otherManager,view.id(),0));conflict(()->notices.withdraw(manager,view.id(),9));
        var withdrawn=notices.withdraw(manager,view.id(),0);assertThat(withdrawn.status()).isEqualTo("WITHDRAWN");
        assertThat(notices.withdraw(manager,view.id(),0).version()).isEqualTo(withdrawn.version());
        assertThat(notices.unread(descendant)).isZero();assertThat(notices.list(employee,false,1,20).list()).noneMatch(n->n.id()==view.id());
        assertThatThrownBy(()->notices.read(employee,view.id())).isInstanceOf(ApiException.class);
    }
    @Test void employeeCannotPublishAndInvalidNoticeInputDoesNotPersist() throws Exception {
        mvc.perform(post("/api/v1/admin/announcements").with(actor(employee,"EMPLOYEE")).with(csrf()).contentType("application/json").content(json.writeValueAsString(announcement(List.of(employee))))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/announcements").with(actor(manager,"ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(announcement(List.of())))).andExpect(status().isBadRequest());
    }
    @Test void onlySuperAdminsCanSaveVersionedPlatformSettings() throws Exception {
        var saved=platform.save(root,new PlatformRequest("QA company","Training contact","help@example.test",false,original.version()));
        assertThat(saved.companyName()).isEqualTo("QA company");assertThat(saved.unreadReminders()).isFalse();
        conflict(()->platform.save(root,new PlatformRequest("Stale","Contact","",true,original.version())));
        denied(()->platform.save(manager,new PlatformRequest("Forbidden","Contact","",true,saved.version())));
        mvc.perform(get("/api/v1/employee/platform").with(actor(employee,"EMPLOYEE"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.contactEmail").value("help@example.test"));
        mvc.perform(put("/api/v1/admin/settings").with(actor(root,"ADMIN")).with(csrf()).contentType("application/json").content("{\"companyName\":\"QA\",\"contactName\":\"Contact\",\"contactEmail\":\"invalid\",\"unreadReminders\":true,\"version\":"+saved.version()+"}")).andExpect(status().isBadRequest());
        assertThat(platform.read().companyName()).isEqualTo("QA company");
    }
    @Test void concurrentRetriesCreateOneFeedbackAndOneAnnouncement() throws Exception {
        var question=question();var notice=announcement(List.of(employee));
        var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Long> create=()->feedback.create(employee,question).id();var a=pool.submit(create);var b=pool.submit(create);assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
            Callable<Long> publish=()->notices.publish(manager,notice).id();var c=pool.submit(publish);var d=pool.submit(publish);assertThat(c.get(15,TimeUnit.SECONDS)).isEqualTo(d.get(15,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
    }
}
