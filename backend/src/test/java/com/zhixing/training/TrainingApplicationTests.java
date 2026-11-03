package com.zhixing.training;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TrainingApplicationTests {
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @Test
    void contextStartsWithFlywayAndLocalFixture() {
        assertThat(context).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class)).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course", Integer.class)).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_chapter", Integer.class)).isGreaterThanOrEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_resource", Integer.class)).isGreaterThanOrEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_enrollment", Integer.class)).isGreaterThanOrEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_progress", Integer.class)).isGreaterThanOrEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question_item", Integer.class)).isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exam_paper", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exam_paper_question", Integer.class)).isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exam_assignment", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_plan", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_assignment", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_plan_course", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_plan_exam", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_certificate", Integer.class)).isGreaterThanOrEqualTo(0);
    }
}
