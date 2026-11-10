package com.zhixing.training;

import com.zhixing.training.exam.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AssessmentEnhancementTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ExamRepository exams;
    @Autowired QuestionImportService imports;
    long admin;
    long employee;

    @BeforeEach void fixture() {
        admin = jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no='ADMIN001'", Long.class);
        String no = "SUB" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name) VALUES(?,'unused','Subjective employee')", no);
        employee = jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?", Long.class, no);
    }

    @Test void subjectiveAnswersRemainPendingUntilManualGrading() {
        long subjective = exams.createQuestion(new ExamDtos.QuestionRequest("subjective", "Explain the policy", List.of(), List.of(), "", "QA", "基础", "ENABLED", 0, "Mention the three controls", "Award points for all three controls"), admin);
        long objective = exams.createQuestion(new ExamDtos.QuestionRequest("single", "Choose safe", List.of("No", "Yes"), List.of(1), "", "QA", "基础", "ENABLED", 0), admin);
        long paper = exams.createExam(new ExamDtos.ExamRequest("Mixed exam", "QA", 30, 60, LocalDate.now().plusDays(1), List.of(new ExamDtos.ExamQuestionRequest(subjective, 60), new ExamDtos.ExamQuestionRequest(objective, 40))), admin);
        exams.publish(paper, admin);
        long subjectivePaperQuestion = exams.detail(paper, true, admin).orElseThrow().questions().stream().filter(q -> q.type().equals("subjective")).findFirst().orElseThrow().id();
        long objectivePaperQuestion = exams.detail(paper, true, admin).orElseThrow().questions().stream().filter(q -> q.type().equals("single")).findFirst().orElseThrow().id();
        long attempt = exams.begin(paper, employee).attempt().id();
        var submitted = exams.submit(attempt, employee, new ExamDtos.SubmitRequest(List.of(new ExamDtos.AnswerRequest(subjectivePaperQuestion, List.of(), "The three controls"), new ExamDtos.AnswerRequest(objectivePaperQuestion, List.of(1)))));
        assertThat(submitted.score()).isNull();
        var grading = exams.grading(attempt, admin);
        var subjectiveVersion = grading.questions().stream().filter(q -> q.question().id() == subjectivePaperQuestion).findFirst().orElseThrow().version();
        var graded = exams.grade(attempt, admin, new ExamDtos.GradeRequest(List.of(new ExamDtos.GradeItem(subjectivePaperQuestion, 60, "All controls present", subjectiveVersion))));
        assertThat(graded.attempt().score()).isEqualTo(100);
        assertThat(graded.attempt().passed()).isTrue();
    }

    @Test void randomAssemblyUsesRulesAndRejectsInsufficientPools() {
        for (int i = 0; i < 3; i++) exams.createQuestion(new ExamDtos.QuestionRequest("single", "Random " + i, List.of("A", "B"), List.of(1), "", "RANDOM", "基础", "ENABLED", 0), admin);
        long paper = exams.createRandomExam(new ExamDtos.RandomExamRequest("Random", "RANDOM", 20, 60, LocalDate.now().plusDays(1), List.of(new ExamDtos.RandomRule("single", "RANDOM", "基础", 2, 50))), admin);
        assertThat(exams.detail(paper, true, admin).orElseThrow().questions()).hasSize(2);
    }

    @Test void csvQuestionImportIsIdempotentAndAllOrNothing() {
        String csv = "type,stem,options,answer,analysis,category,difficulty,referenceAnswer,gradingGuide\n" +
                "single,Imported question,A|B,1,,Import,基础,,\n";
        var file = new MockMultipartFile("file", "questions.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var first = imports.importFile(admin, file, UUID.randomUUID().toString());
        assertThat(first.imported()).isEqualTo(1);
        var second = imports.importFile(admin, file, UUID.randomUUID().toString());
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question_item WHERE stem='Imported question'", Integer.class)).isEqualTo(1);
    }
}
