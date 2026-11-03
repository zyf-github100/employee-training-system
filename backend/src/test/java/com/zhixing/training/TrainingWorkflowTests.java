package com.zhixing.training;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.course.*;
import com.zhixing.training.exam.*;
import com.zhixing.training.plan.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TrainingWorkflowTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ExamRepository exams;
    @Autowired LearningRepository learning;
    @Autowired CourseRepository courses;
    @Autowired PlanRepository plans;
    @Autowired MockMvc mvc;
    long admin, employee, other, paper, question, paperQuestion, course, chapter;

    @BeforeEach void fixture() {
        admin = jdbc.queryForObject("SELECT id FROM sys_user WHERE role_code='ADMIN' ORDER BY id LIMIT 1", Long.class);
        employee = employee(); other = employee();
        question = exams.createQuestion(new ExamDtos.QuestionRequest("single","Choose the safe option",List.of("unsafe","safe"),List.of(1),"SECRET_REVIEW: choose safe","QA","基础","ENABLED",0),admin);
        paper = exams.createExam(new ExamDtos.ExamRequest("QA exam", "QA",1,60,LocalDate.now().plusDays(1),List.of(new ExamDtos.ExamQuestionRequest(question,100))),admin);
        exams.publish(paper,admin);
        paperQuestion = exams.detail(paper,true,admin).orElseThrow().questions().get(0).id();
        course = courses.insert(new CourseDtos.CourseRequest("QA course","Summary",null,"QA","BEGINNER","PUBLISHED",10,0),admin);
        chapter = learning.insertChapter(course,new LearningDtos.ChapterRequest("Read this",1,"Training content",10));
    }

    private long employee() {
        String no = "Q"+UUID.randomUUID().toString().replace("-", "").substring(0,24);
        jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name) VALUES(?,'unused','QA employee')",no);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);
    }
    private List<ExamDtos.AnswerRequest> answers(int index) { return List.of(new ExamDtos.AnswerRequest(paperQuestion,List.of(index))); }
    private long plan(boolean optionalExam) {
        long id = plans.create(new PlanDtos.PlanRequest("QA plan","Complete required items","QA",LocalDate.now(),LocalDate.now().plusDays(2),List.of(new PlanDtos.CourseItemRequest(course,true)),List.of(new PlanDtos.ExamItemRequest(paper,!optionalExam))),admin);
        plans.publish(id,admin,List.of(employee,employee));
        return id;
    }
    private void finishCourse() {
        learning.enrollment(course,employee);
        learning.updateProgress(course,chapter,employee,new LearningDtos.ProgressRequest(100,25,true));
    }
    private void race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { ready.countDown(); go.await(); return first.call(); });
            var b = pool.submit(() -> { ready.countDown(); go.await(); return second.call(); });
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown();
            a.get(15,TimeUnit.SECONDS); b.get(15,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    @Test void employeeResponsesHideReviewUntilSubmissionAndProtectOwnership() throws Exception {
        var auth = jwt().jwt(j -> j.subject(""+employee)).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        mvc.perform(get("/api/v1/employee/exams/{id}",paper).with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.questions[0].analysis").doesNotExist()).andExpect(jsonPath("$.data.questions[0].answer").doesNotExist());
        long attempt = exams.begin(paper,employee).attempt().id();
        mvc.perform(get("/api/v1/employee/exam-attempts/{id}",attempt).with(auth)).andExpect(jsonPath("$.data.questions[0].analysis").doesNotExist());
        mvc.perform(put("/api/v1/employee/exam-attempts/{id}/answers",attempt).with(auth).with(csrf()).contentType("application/json").content("{\"answers\":[{\"paperQuestionId\":"+paperQuestion+",\"answer\":[1]}]}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.questions[0].analysis").doesNotExist());
        mvc.perform(get("/api/v1/employee/exam-attempts/{id}",attempt).with(jwt().jwt(j -> j.subject(""+other)).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/questions").with(auth)).andExpect(status().isForbidden());
        exams.submit(attempt,employee,new ExamDtos.SubmitRequest(answers(1)));
        mvc.perform(get("/api/v1/employee/exam-attempts/{id}",attempt).with(auth)).andExpect(jsonPath("$.data.questions[0].analysis").value("SECRET_REVIEW: choose safe"));
        // The generic paper endpoint never becomes a review endpoint.
        mvc.perform(get("/api/v1/employee/exams/{id}",paper).with(auth)).andExpect(jsonPath("$.data.questions[0].analysis").doesNotExist());
        assertThat(exams.detail(paper,true,admin).orElseThrow().questions().get(0).analysis()).contains("SECRET_REVIEW");
    }

    @Test void immutablePaperAndDuplicateSubmitKeepSameGrade() {
        long attempt = exams.begin(paper,employee).attempt().id();
        exams.updateQuestion(question,new ExamDtos.QuestionRequest("single","Changed source",List.of("unsafe","safe"),List.of(0),"Changed","QA","基础","ENABLED",0));
        var result = exams.submit(attempt,employee,new ExamDtos.SubmitRequest(answers(1)));
        var repeated = exams.submit(attempt,employee,new ExamDtos.SubmitRequest(answers(0)));
        assertThat(result.score()).isEqualTo(100); assertThat(repeated).isEqualTo(result);
        assertThat(exams.attemptDetail(attempt,employee).orElseThrow().answers().get(paperQuestion)).containsExactly(1);
    }

    @Test void startsAreSerialized() throws Exception {
        race(() -> exams.begin(paper,employee), () -> exams.begin(paper,employee));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exam_attempt WHERE paper_id=? AND user_id=?",Integer.class,paper,employee)).isEqualTo(1);
    }

    @Test void concurrentSaveAndSubmitCannotOverwriteFinalAnswers() throws Exception {
        long attempt = exams.begin(paper,employee).attempt().id();
        race(() -> exams.saveAnswers(attempt,employee,new ExamDtos.SaveAnswersRequest(answers(0))), () -> exams.submit(attempt,employee,new ExamDtos.SubmitRequest(answers(1))));
        var result = exams.attemptDetail(attempt,employee).orElseThrow();
        assertThat(result.attempt().score()).isEqualTo(100);
        assertThat(result.answers().get(paperQuestion)).containsExactly(1);
        assertThat(jdbc.queryForObject("SELECT score FROM exam_answer WHERE attempt_id=?",Integer.class,attempt)).isEqualTo(100);
    }

    @Test void schedulerFinalizesClosedBrowserAndIgnoresLatePayload() throws Exception {
        long attempt = exams.begin(paper,employee).attempt().id();
        exams.saveAnswers(attempt,employee,new ExamDtos.SaveAnswersRequest(answers(1)));
        jdbc.update("UPDATE exam_attempt SET started_at=TIMESTAMPADD(MINUTE,-2,CURRENT_TIMESTAMP) WHERE id=?",attempt);
        race(() -> { new ExamExpiryJob(exams).expire(); return null; }, () -> exams.submit(attempt,employee,new ExamDtos.SubmitRequest(answers(0))));
        assertThat(exams.attempt(attempt).status()).isEqualTo("SUBMITTED");
        assertThat(exams.attempt(attempt).score()).isEqualTo(100);
        assertThat(exams.activeAttempts(employee)).isEmpty();
    }

    @Test void schedulerRunsWithoutAnyEmployeeRead() {
        long attempt = exams.begin(paper,employee).attempt().id();
        jdbc.update("UPDATE exam_attempt SET started_at=TIMESTAMPADD(MINUTE,-2,CURRENT_TIMESTAMP) WHERE id=?",attempt);
        new ExamExpiryJob(exams).expire();
        assertThat(jdbc.queryForObject("SELECT status FROM exam_attempt WHERE id=?",String.class,attempt)).isEqualTo("SUBMITTED");
    }

    @Test void requiredCourseAndExamCompletePlanAndIssueOneCertificateConcurrently() throws Exception {
        long plan = plan(false);
        learning.enrollment(course,employee);
        long attempt = exams.begin(paper,employee).attempt().id();
        race(() -> learning.updateProgress(course,chapter,employee,new LearningDtos.ProgressRequest(100,25,true)), () -> exams.submit(attempt,employee,new ExamDtos.SubmitRequest(answers(1))));
        assertThat(plans.detail(plan,false,employee).orElseThrow().plan().progressPercent()).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_certificate WHERE plan_id=? AND user_id=?",Integer.class,plan,employee)).isEqualTo(1);
        String no=jdbc.queryForObject("SELECT certificate_no FROM training_certificate WHERE plan_id=? AND user_id=?",String.class,plan,employee);
        finishCourse(); plans.refreshProgress(plan,employee);
        assertThat(jdbc.queryForObject("SELECT certificate_no FROM training_certificate WHERE plan_id=? AND user_id=?",String.class,plan,employee)).isEqualTo(no);
    }

    @Test void optionalItemsDoNotBlockCompletionAndProgressCannotRegress() {
        long plan = plan(true); finishCourse();
        learning.updateProgress(course,chapter,employee,new LearningDtos.ProgressRequest(20,10,false));
        assertThat(learning.detail(course,employee,true).progressPercent()).isEqualTo(100);
        var detail=plans.detail(plan,false,employee).orElseThrow();
        assertThat(detail.plan().progressPercent()).isEqualTo(100);
        assertThat(detail.plan().totalItems()).isEqualTo(1);
        assertThat(detail.items().get(1).progressPercent()).isZero();
    }

    @Test void rosterIsExplicitDeduplicatedAndIdempotent() {
        long plan=plan(true);
        assertThat(plans.detail(plan,false,other)).isEmpty();
        assertThat(plans.publish(plan,admin,List.of(employee)).plan().assigneeCount()).isEqualTo(1);
        assertThatThrownBy(() -> plans.publish(plan,admin,List.of(other))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> plans.publish(plan,admin,List.of())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> learning.updateProgress(course,chapter,other,new LearningDtos.ProgressRequest(100,0,true))).isInstanceOf(ApiException.class);
    }

    @Test void publishingAfterCompletionImmediatelyIssuesCertificate() {
        finishCourse(); long plan=plan(true);
        assertThat(plans.detail(plan,false,employee).orElseThrow().plan().progressPercent()).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_certificate WHERE plan_id=?",Integer.class,plan)).isEqualTo(1);
    }

    @Test void reopeningExpiredAttemptCommitsItsTerminalResult() {
        long attempt=exams.begin(paper,employee).attempt().id();
        exams.saveAnswers(attempt,employee,new ExamDtos.SaveAnswersRequest(answers(1)));
        jdbc.update("UPDATE exam_attempt SET started_at=TIMESTAMPADD(MINUTE,-2,CURRENT_TIMESTAMP) WHERE id=?",attempt);
        assertThat(exams.begin(paper,employee).attempt().status()).isEqualTo("SUBMITTED");
        assertThat(exams.attempt(attempt).score()).isEqualTo(100);
    }

    @Test void hundredPercentProgressAlwaysHasACompletionFact() {
        learning.enrollment(course,employee);
        var result=learning.updateProgress(course,chapter,employee,new LearningDtos.ProgressRequest(100,10,false));
        assertThat(result.completed()).isTrue();
        assertThat(learning.detail(course,employee,true).completedChapters()).isEqualTo(1);
    }

    @Test void withdrawnCourseCannotReceiveNewProgress() {
        learning.enrollment(course,employee);
        jdbc.update("UPDATE course SET status='DRAFT' WHERE id=?",course);
        assertThatThrownBy(() -> learning.updateProgress(course,chapter,employee,new LearningDtos.ProgressRequest(100,10,true))).isInstanceOf(ApiException.class);
        assertThat(learning.progress(course,employee)).isEmpty();
    }
}
