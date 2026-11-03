package com.zhixing.training.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Local/test-only seed data so the connected UI has a complete assessment flow on first start. */
@Component
@Profile("local | test")
@Order(20)
public class ExamDemoSeeder implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public ExamDemoSeeder(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void run(String... args) throws Exception {
        Boolean enabled = jdbc.queryForObject("SELECT COUNT(*) > 0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='question_item'", Boolean.class);
        if (!Boolean.TRUE.equals(enabled) || count("SELECT COUNT(*) FROM question_item") > 0) return;
        long admin = jdbc.queryForObject("SELECT id FROM sys_user WHERE role_code='ADMIN' ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO question_item(question_type,stem,options_json,answer_json,analysis,category,difficulty,status,created_by) VALUES(?,?,?,?,?,?,?,?,?)", "single", "发现办公账号异常登录时，第一步应该做什么？", json(List.of("继续使用并观察", "立即报告并修改密码", "把密码发给同事确认", "删除所有邮件")), json(List.of(1)), "先报告异常并保护凭证，避免扩大影响。", "安全合规", "基础", "ENABLED", admin);
        jdbc.update("INSERT INTO question_item(question_type,stem,options_json,answer_json,analysis,category,difficulty,status,created_by) VALUES(?,?,?,?,?,?,?,?,?)", "multiple", "以下哪些做法属于安全的文件共享方式？", json(List.of("使用企业网盘权限分享", "将敏感文件发到公开群聊", "给外部人员设置有效期链接", "使用个人邮箱长期保存")), json(List.of(0,2)), "企业网盘和带有效期的受控链接便于审计和撤销。", "安全合规", "基础", "ENABLED", admin);
        jdbc.update("INSERT INTO question_item(question_type,stem,options_json,answer_json,analysis,category,difficulty,status,created_by) VALUES(?,?,?,?,?,?,?,?,?)", "boolean", "离开工位时应锁定电脑屏幕。", json(List.of("正确", "错误")), json(List.of(0)), "锁屏可以降低未授权访问风险。", "安全合规", "基础", "ENABLED", admin);
        KeyHolder paperKey = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("INSERT INTO exam_paper(title,category,duration_minutes,pass_score,deadline,status,created_by,published_at) VALUES(?,?,?,?,?,'PUBLISHED',?,CURRENT_TIMESTAMP)", new String[]{"id"});
            ps.setString(1, "新员工安全合规测评"); ps.setString(2, "安全合规"); ps.setInt(3, 30); ps.setInt(4, 60); ps.setObject(5, LocalDate.now().plusDays(30)); ps.setLong(6, admin); return ps;
        }, paperKey);
        long paper = paperKey.getKey().longValue();
        List<Long> questions = jdbc.query("SELECT id FROM question_item ORDER BY id", (rs, row) -> rs.getLong(1));
        int[] points = {34,33,33};
        for (int i = 0; i < questions.size(); i++) jdbc.update("INSERT INTO exam_paper_question(paper_id,question_id,sort_no,points,question_type,stem,options_json,answer_json,analysis) SELECT ?,id,?,?,question_type,stem,options_json,answer_json,analysis FROM question_item WHERE id=?", paper, i + 1, points[i], questions.get(i));
        jdbc.update("INSERT INTO exam_assignment(paper_id,user_id) SELECT ?,id FROM sys_user WHERE role_code='EMPLOYEE' AND activated=TRUE AND status='ACTIVE' ON DUPLICATE KEY UPDATE paper_id=VALUES(paper_id)", paper);
    }
    private long count(String sql){Long n=jdbc.queryForObject(sql,Long.class);return n==null?0:n;}
    private String json(Object value) throws Exception { return mapper.writeValueAsString(value); }
}
