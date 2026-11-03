package com.zhixing.training.config;

import com.zhixing.training.auth.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Component
@Profile("local | test")
@Order(10)
public class LocalDemoSeeder implements CommandLineRunner {
    private final JdbcTemplate jdbc; private final UserRepository users; private final PasswordEncoder encoder;
    @Value("${zhixing.demo-seed:false}") private boolean enabled;
    public LocalDemoSeeder(JdbcTemplate jdbc, UserRepository users, PasswordEncoder encoder){this.jdbc=jdbc;this.users=users;this.encoder=encoder;}
    @Override @org.springframework.transaction.annotation.Transactional public void run(String... args) {
        if (!enabled || count("SELECT COUNT(*) FROM sys_user") > 0) return;
        Long existing = jdbc.query("SELECT id FROM org_department WHERE code='TECH'", (rs,row) -> rs.getLong(1)).stream().findFirst().orElse(null);
        long dept;
        if (existing != null) dept = existing;
        else {
            org.springframework.jdbc.support.KeyHolder key = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(connection -> connection.prepareStatement("INSERT INTO org_department(name,code) VALUES('技术与产品部','TECH')", new String[]{"id"}), key);
            dept = key.getKey().longValue();
        }
        // Local-only credentials for smoke testing. Change them before connecting a shared environment.
        long adminId = users.insert("ADMIN001", encoder.encode("Zhixing@123"), "系统管理员", "ADMIN", dept);
        jdbc.update("UPDATE sys_user SET super_admin=TRUE,data_scope='ALL' WHERE id=?",adminId);
        users.insert("E20260001", encoder.encode("Zhixing@123"), "演示员工", "EMPLOYEE", dept);
        jdbc.update("INSERT INTO course(title,summary,category,difficulty,status,duration_minutes,chapter_count,published_at,created_by) VALUES(?,?,?,?,?,?,?,?,?)", "新员工安全与合规", "本地联调示例课程，仅含基础信息，资源与章节尚未接入", "入职培训", "BEGINNER", "PUBLISHED", 45, 0, java.sql.Timestamp.from(java.time.Instant.now()), adminId);
        jdbc.update("INSERT INTO course(title,summary,category,difficulty,status,duration_minutes,chapter_count,created_by) VALUES(?,?,?,?,?,?,?,?)", "项目协作基础", "掌握跨部门协作流程与沟通规范", "通用能力", "BEGINNER", "DRAFT", 30, 0, adminId);
        // Flyway runs before this seeder on an empty database. Insert fixture
        // chapters here rather than relying on migrations to find future rows.
        seedChapter("新员工安全与合规", "安全与合规导学", 1, "了解企业安全规范与学习目标。", 12);
        seedChapter("新员工安全与合规", "账号与数据安全", 2, "掌握账号、密码和数据使用规范。", 18);
        seedChapter("新员工安全与合规", "完成入职安全检查", 3, "完成课程复盘与安全承诺。", 15);
        seedChapter("项目协作基础", "协作流程概览", 1, "认识项目协作的基本流程。", 15);
        seedChapter("项目协作基础", "沟通与交付规范", 2, "掌握跨部门沟通和交付规范。", 15);
        jdbc.update("UPDATE course SET chapter_count=(SELECT COUNT(*) FROM course_chapter cc WHERE cc.course_id=course.id) WHERE created_by=?",adminId);
        jdbc.update("UPDATE course SET summary='学习企业安全规范、账号与数据保护，并完成入职安全检查。' WHERE title='新员工安全与合规' AND created_by=?",adminId);
    }
    private void seedChapter(String course, String title, int sort, String content, int minutes) {
        jdbc.update("INSERT INTO course_chapter(course_id,title,sort_no,content_markdown,duration_minutes) SELECT id,?,?,?,? FROM course WHERE title=?",title,sort,content,minutes,course);
    }
    private long count(String sql){Long n=jdbc.queryForObject(sql,Long.class);return n==null?0:n;}
}
