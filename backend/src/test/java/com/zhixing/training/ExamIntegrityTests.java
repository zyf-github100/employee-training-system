package com.zhixing.training;

import com.zhixing.training.exam.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ExamIntegrityTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ExamRepository exams;
    @Autowired ExamIntegrityService integrity;
    long admin; long employee; long paper;

    @BeforeEach void fixture() {
        admin=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no='ADMIN001'",Long.class);
        String no="INT"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name) VALUES(?,'unused','Integrity employee')",no);
        employee=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);
        long q=exams.createQuestion(new ExamDtos.QuestionRequest("single","Integrity question",List.of("A","B"),List.of(0),"","Integrity","基础","ENABLED",0),admin);
        paper=exams.createExam(new ExamDtos.ExamRequest("Integrity exam","Integrity",10,60,LocalDate.now().plusDays(1),List.of(new ExamDtos.ExamQuestionRequest(q,100))),admin);
        exams.publish(paper,admin);
    }

    @Test void focusLossesAreRecordedAndFlaggedForReview() {
        long attempt=exams.begin(paper,employee).attempt().id();
        for(int i=0;i<5;i++) integrity.record(employee,attempt,new ExamDtos.IntegrityEventRequest("FOCUS_LOST",null,"{\"sessionId\":\"one\"}"));
        assertThat(integrity.view(attempt).status()).isEqualTo("REVIEW");
        assertThat(integrity.view(attempt).events()).extracting(ExamDtos.IntegrityEventView::sequenceNo).containsExactly(1,2,3,4,5);
        integrity.disposition(admin,attempt,new ExamDtos.IntegrityDispositionRequest("DISMISSED","复核确认误报"));
        assertThat(integrity.view(attempt).status()).isEqualTo("DISMISSED");
    }
}
