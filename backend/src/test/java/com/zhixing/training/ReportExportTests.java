package com.zhixing.training;

import org.apache.poi.ss.usermodel.Cell;import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReportExportTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    long admin, employee, paper, plan;
    String tag, employeeNo, courseTitle, chapterTitle, examTitle, planTitle;

    @BeforeEach void fixture() {
        tag = UUID.randomUUID().toString().substring(0, 8);
        admin = jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no='ADMIN001'", Long.class);
        long dept = jdbc.queryForObject("SELECT id FROM org_department ORDER BY id LIMIT 1", Long.class);

        employeeNo = "R" + tag + "E";
        jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name,department_id) VALUES(?,?,?,?)",
                employeeNo, "!", "Report employee " + tag, dept);
        employee = jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?", Long.class, employeeNo);

        courseTitle = "Report course " + tag;
        jdbc.update("INSERT INTO course(title,status,duration_minutes,chapter_count) VALUES(?,?,?,?)",
                courseTitle, "PUBLISHED", 60, 1);
        long course = jdbc.queryForObject("SELECT id FROM course WHERE title=?", Long.class, courseTitle);
        chapterTitle = "Report chapter " + tag;
        jdbc.update("INSERT INTO course_chapter(course_id,title,sort_no,duration_minutes) VALUES(?,?,?,?)",
                course, chapterTitle, 1, 60);
        long chapter = jdbc.queryForObject("SELECT id FROM course_chapter WHERE course_id=? AND title=?",
                Long.class, course, chapterTitle);
        jdbc.update("INSERT INTO course_enrollment(course_id,user_id,status,progress_percent,completed_chapters,completed_at) VALUES(?,?,'COMPLETED',100,1,NOW())",
                course, employee);
        long enrollment = jdbc.queryForObject("SELECT id FROM course_enrollment WHERE course_id=? AND user_id=?",
                Long.class, course, employee);
        jdbc.update("INSERT INTO study_progress(enrollment_id,chapter_id,progress_percent,last_position_seconds,watched_seconds,last_reported_at,completed_at) VALUES(?,?,100,3600,3600,NOW(),NOW())",
                enrollment, chapter);

        examTitle = "Report exam " + tag;
        jdbc.update("INSERT INTO exam_paper(title,category,duration_minutes,pass_score,deadline,status) VALUES(?,?,?,?,?,?)",
                examTitle, "QA", 30, 60, LocalDate.now().plusDays(1), "PUBLISHED");
        paper = jdbc.queryForObject("SELECT id FROM exam_paper WHERE title=?", Long.class, examTitle);
        jdbc.update("INSERT INTO exam_attempt(paper_id,user_id,attempt_no,status,score,passed,started_at,submitted_at,grading_status,integrity_status) VALUES(?,?,1,'SUBMITTED',88,TRUE,NOW(),NOW(),'COMPLETE','REVIEW')",
                paper, employee);

        planTitle = "Report plan " + tag;
        jdbc.update("INSERT INTO training_plan(title,category,start_date,end_date,status) VALUES(?,?,?,?,?)",
                planTitle, "QA", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2), "PUBLISHED");
        plan = jdbc.queryForObject("SELECT id FROM training_plan WHERE title=?", Long.class, planTitle);
        jdbc.update("INSERT INTO training_assignment(plan_id,user_id,status,progress_percent,completed_at) VALUES(?,?,'COMPLETED',100,NOW())",
                plan, employee);
    }

    @Test void employeeArchiveReportContainsAggregates() throws Exception {
        Sheet sheet = export("/employees", "{}", admin(admin));
        int row = findEmployeeRow(sheet, employeeNo);
        assertThat(row).isGreaterThanOrEqualTo(3);
        assertThat(text(sheet, row, headerIndex(sheet, "姓名"))).isEqualTo("Report employee " + tag);
        assertThat(number(sheet, row, headerIndex(sheet, "报名课程"))).isEqualTo(1);
        assertThat(number(sheet, row, headerIndex(sheet, "完成课程"))).isEqualTo(1);
        assertThat(number(sheet, row, headerIndex(sheet, "完成课程时长(分钟)"))).isEqualTo(60);
        assertThat(number(sheet, row, headerIndex(sheet, "考试次数"))).isEqualTo(1);
        assertThat(number(sheet, row, headerIndex(sheet, "通过次数"))).isEqualTo(1);
        assertThat(number(sheet, row, headerIndex(sheet, "平均分"))).isEqualTo(88);
        assertThat(number(sheet, row, headerIndex(sheet, "培训计划数"))).isEqualTo(1);
        assertThat(number(sheet, row, headerIndex(sheet, "完成计划数"))).isEqualTo(1);
        assertThat(number(sheet, row, headerIndex(sheet, "证书数"))).isZero();
        assertThat(text(sheet, row, headerIndex(sheet, "账号状态"))).isEqualTo("启用");
    }

    @Test void learningExamAndPlanReportsContainFacts() throws Exception {
        Sheet learning = export("/learning", "{}", admin(admin));
        int learningRow = findEmployeeRow(learning, employeeNo);
        assertThat(text(learning, learningRow, headerIndex(learning, "课程"))).isEqualTo(courseTitle);
        assertThat(text(learning, learningRow, headerIndex(learning, "章节"))).isEqualTo(chapterTitle);
        assertThat(number(learning, learningRow, headerIndex(learning, "进度(%)"))).isEqualTo(100);
        assertThat(number(learning, learningRow, headerIndex(learning, "学习时长(分钟)"))).isEqualTo(60.0);
        assertThat(text(learning, learningRow, headerIndex(learning, "是否完成"))).isEqualTo("是");

        Sheet exams = export("/exams", "{}", admin(admin));
        int examRow = findEmployeeRow(exams, employeeNo);
        assertThat(text(exams, examRow, headerIndex(exams, "考试"))).isEqualTo(examTitle);
        assertThat(number(exams, examRow, headerIndex(exams, "分数"))).isEqualTo(88);
        assertThat(text(exams, examRow, headerIndex(exams, "是否通过"))).isEqualTo("是");
        assertThat(text(exams, examRow, headerIndex(exams, "完整性"))).isEqualTo("待复核");
        assertThat(text(exams, examRow, headerIndex(exams, "阅卷状态"))).isEqualTo("已完成");

        Sheet plans = export("/plans", "{}", admin(admin));
        int planRow = findEmployeeRow(plans, employeeNo);
        assertThat(text(plans, planRow, headerIndex(plans, "培训计划"))).isEqualTo(planTitle);
        assertThat(text(plans, planRow, headerIndex(plans, "计划状态"))).isEqualTo("已发布");
        assertThat(text(plans, planRow, headerIndex(plans, "任务状态"))).isEqualTo("已完成");
        assertThat(number(plans, planRow, headerIndex(plans, "进度(%)"))).isEqualTo(100);
    }

    @Test void dateFilterAndInvalidRangeAreHandled() throws Exception {
        Sheet sheet = export("/learning", "{\"from\":\"" + LocalDate.now().plusDays(1) + "\"}", admin(admin));
        assertThat(sheet.getLastRowNum()).isEqualTo(2);

        mvc.perform(post("/api/v1/admin/reports/learning").with(admin(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2026-11-20\",\"to\":\"2026-11-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_QUERY"));
    }

    @Test void reportRespectsScopeAndRejectsEmployee() throws Exception {
        long deptA = department("A" + tag);
        long deptB = department("B" + tag);
        long employeeA = user(deptA, "R" + tag + "A");
        long employeeB = user(deptB, "R" + tag + "B");
        long scopedAdmin = user(deptA, "R" + tag + "M");
        jdbc.update("UPDATE sys_user SET role_code='ADMIN',data_scope='DEPARTMENT' WHERE id=?", scopedAdmin);

        Sheet sheet = export("/employees", "{}", admin(scopedAdmin));
        assertThat(findEmployeeRow(sheet, "R" + tag + "A")).isGreaterThanOrEqualTo(3);
        assertThat(findEmployeeRow(sheet, "R" + tag + "B")).isEqualTo(-1);

        mvc.perform(post("/api/v1/admin/reports/employees").with(actor(employee, "EMPLOYEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/reports/employees").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private Sheet export(String path, String body, RequestPostProcessor actor) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/reports" + path).with(actor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")))
                .andReturn();
        return new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray())).getSheetAt(0);
    }

    private RequestPostProcessor admin(long id) {
        return actor(id, "ADMIN");
    }

    private RequestPostProcessor actor(long id, String role) {
        return jwt().jwt(j -> j.subject("" + id)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private long department(String suffix) {
        String code = "RP" + suffix;
        jdbc.update("INSERT INTO org_department(name,code,status) VALUES(?,?, 'ACTIVE')", "Dept " + suffix, code);
        return jdbc.queryForObject("SELECT id FROM org_department WHERE code=?", Long.class, code);
    }

    private long user(long dept, String no) {
        jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name,department_id) VALUES(?,?,?,?)",
                no, "!", "Scope user " + no, dept);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?", Long.class, no);
    }

    private static int headerIndex(Sheet sheet, String header) {
        Row row = sheet.getRow(2);
        for (int i = 0; i < row.getLastCellNum(); i++)
            if (header.equals(text(row.getCell(i)))) return i;
        throw new AssertionError("Missing header: " + header);
    }

    private static int findEmployeeRow(Sheet sheet, String employeeNo) {
        int column = headerIndex(sheet, "工号");
        for (int i = 3; i <= sheet.getLastRowNum(); i++)
            if (employeeNo.equals(text(sheet.getRow(i).getCell(column)))) return i;
        return -1;
    }

    private static String text(Sheet sheet, int row, int column) {
        return text(sheet.getRow(row).getCell(column));
    }

    private static String text(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf(cell.getNumericCellValue());
        return "";
    }

    private static double number(Sheet sheet, int row, int column) {
        Cell cell = sheet.getRow(row).getCell(column);
        return cell.getNumericCellValue();
    }
}
