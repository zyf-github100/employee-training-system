package com.zhixing.training.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("local | test")
@Order(30)
public class PlanDemoSeeder implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    public PlanDemoSeeder(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override @org.springframework.transaction.annotation.Transactional public void run(String... args){
        Boolean table=jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='training_plan'",Boolean.class);
        if(!Boolean.TRUE.equals(table)||count("SELECT COUNT(*) FROM training_plan")>0)return;
        Long admin=jdbc.queryForObject("SELECT id FROM sys_user WHERE role_code='ADMIN' ORDER BY id LIMIT 1",Long.class);
        Long course=jdbc.queryForObject("SELECT id FROM course WHERE title='新员工安全与合规' LIMIT 1",Long.class);
        Long exam=jdbc.queryForObject("SELECT id FROM exam_paper WHERE status='PUBLISHED' ORDER BY id LIMIT 1",Long.class);
        if(course==null||exam==null||admin==null)return;
        jdbc.update("INSERT INTO training_plan(title,description,category,start_date,end_date,status,created_by,published_at) VALUES(?,?,?,?,?,'PUBLISHED',?,CURRENT_TIMESTAMP)","新员工入职安全培训计划","完成安全合规课程并通过阶段测评。","入职培训", LocalDate.now(),LocalDate.now().plusDays(30),admin);
        long plan=jdbc.queryForObject("SELECT id FROM training_plan ORDER BY id DESC LIMIT 1",Long.class);
        jdbc.update("INSERT INTO training_plan_course(plan_id,course_id,sort_no,required) VALUES(?,?,1,TRUE)",plan,course);
        jdbc.update("INSERT INTO training_plan_exam(plan_id,paper_id,sort_no,required) VALUES(?,?,1,TRUE)",plan,exam);
        jdbc.update("INSERT INTO training_assignment(plan_id,user_id) SELECT ?,id FROM sys_user WHERE role_code='EMPLOYEE' AND activated=TRUE AND status='ACTIVE' ON DUPLICATE KEY UPDATE plan_id=VALUES(plan_id)",plan);
    }
    private long count(String sql){Long value=jdbc.queryForObject(sql,Long.class);return value==null?0:value;}
}
