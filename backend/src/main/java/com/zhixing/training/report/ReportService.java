package com.zhixing.training.report;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Service
public class ReportService {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }

    public ReportFile employees(long actor, ReportDtos.ReportQuery query) {
        ReportDtos.ReportQuery q = normalize(query);
        repository.validate(q);
        List<List<Object>> rows = repository.employees(actor, q).stream().map(row -> line(
                row.employeeNo(), row.name(), row.department(), activeStatus(row.status()),
                row.activated() ? "已激活" : "未激活", row.enrolled(), row.completed(), row.completedMinutes(),
                row.attempts(), row.submitted(), row.passed(),
                row.submitted() == 0 ? null : row.averageScore(),
                row.plans(), row.plansCompleted(), row.certificates(), row.lastActive())).toList();
        return report("员工档案", "员工培训档案", List.of(
                "工号", "姓名", "部门", "账号状态", "激活状态", "报名课程", "完成课程", "完成课程时长(分钟)",
                "考试次数", "已交卷", "通过次数", "平均分", "培训计划数", "完成计划数", "证书数", "最近学习时间"),
                rows, new int[]{14, 12, 16, 10, 10, 10, 10, 16, 10, 10, 10, 10, 12, 12, 10, 20}, "员工培训档案");
    }

    public ReportFile learning(long actor, ReportDtos.ReportQuery query) {
        ReportDtos.ReportQuery q = normalize(query);
        repository.validate(q);
        List<List<Object>> rows = repository.learning(actor, q).stream().map(row -> line(
                row.employeeNo(), row.name(), row.department(), row.course(), row.chapter(),
                row.progressPercent(), minutes(row.watchedSeconds()), row.completedAt() == null ? "否" : "是",
                row.completedAt(), row.lastActivity())).toList();
        return report("学习明细", "员工学习明细", List.of(
                "工号", "姓名", "部门", "课程", "章节", "进度(%)", "学习时长(分钟)", "是否完成", "完成时间", "最后学习时间"),
                rows, new int[]{14, 12, 16, 26, 26, 10, 14, 10, 20, 20}, "学习明细");
    }

    public ReportFile exams(long actor, ReportDtos.ReportQuery query) {
        ReportDtos.ReportQuery q = normalize(query);
        repository.validate(q);
        List<List<Object>> rows = repository.exams(actor, q).stream().map(row -> line(
                row.employeeNo(), row.name(), row.department(), row.exam(), row.attemptNo(), "已交卷",
                row.score(), yesNo(row.passed()), row.startedAt(), row.submittedAt(),
                integrity(row.integrityStatus()), grading(row.gradingStatus()))).toList();
        return report("考试成绩", "考试成绩明细", List.of(
                "工号", "姓名", "部门", "考试", "第几次", "状态", "分数", "是否通过", "开始时间", "交卷时间",
                "完整性", "阅卷状态"),
                rows, new int[]{14, 12, 16, 26, 8, 10, 8, 10, 20, 20, 12, 12}, "考试成绩");
    }

    public ReportFile plans(long actor, ReportDtos.ReportQuery query) {
        ReportDtos.ReportQuery q = normalize(query);
        repository.validate(q);
        List<List<Object>> rows = repository.plans(actor, q).stream().map(row -> line(
                row.employeeNo(), row.name(), row.department(), row.plan(), planStatus(row.planStatus()),
                assignmentStatus(row.assignmentStatus()), row.progressPercent(), row.startedAt(),
                row.completedAt(), row.planStart(), row.planEnd(), row.terminationReason())).toList();
        return report("计划完成", "培训计划完成情况", List.of(
                "工号", "姓名", "部门", "培训计划", "计划状态", "任务状态", "进度(%)", "开始学习时间",
                "完成时间", "计划开始", "计划结束", "终止原因"),
                rows, new int[]{14, 12, 16, 28, 10, 10, 10, 20, 20, 12, 12, 26}, "计划完成情况");
    }

    private ReportFile report(String sheet, String title, List<String> headers, List<List<Object>> rows,
                              int[] widths, String filename) {
        byte[] content = ReportWorkbook.write(sheet, title, headers, rows, widths);
        return new ReportFile(content, filename + "-" + LocalDate.now().format(FILE_DATE) + ".xlsx");
    }

    private static ReportDtos.ReportQuery normalize(ReportDtos.ReportQuery query) {
        return query == null ? new ReportDtos.ReportQuery(null, null, null, null) : query;
    }

    private static String activeStatus(String value) {
        return switch (value == null ? "" : value) {
            case "ACTIVE" -> "启用";
            case "DISABLED" -> "停用";
            default -> value == null ? "" : value;
        };
    }

    private static String planStatus(String value) {
        return switch (value == null ? "" : value) {
            case "PUBLISHED" -> "已发布";
            case "DRAFT" -> "草稿";
            case "TERMINATED" -> "已终止";
            default -> value == null ? "" : value;
        };
    }

    private static String assignmentStatus(String value) {
        return switch (value == null ? "" : value) {
            case "ASSIGNED" -> "待开始";
            case "IN_PROGRESS" -> "进行中";
            case "COMPLETED" -> "已完成";
            default -> value == null ? "" : value;
        };
    }

    private static String integrity(String value) {
        return switch (value == null ? "" : value) {
            case "CLEAR" -> "正常";
            case "REVIEW" -> "待复核";
            case "DISMISSED" -> "已排除";
            default -> value == null ? "" : value;
        };
    }

    private static String grading(String value) {
        return switch (value == null ? "" : value) {
            case "AUTO" -> "自动判分";
            case "PENDING" -> "待阅卷";
            case "COMPLETE" -> "已完成";
            default -> value == null ? "" : value;
        };
    }

    private static String yesNo(Boolean value) {
        return value == null ? "" : value ? "是" : "否";
    }

    private static double minutes(int seconds) {
        return Math.round(seconds * 10.0 / 60) / 10.0;
    }

    private static List<Object> line(Object... values) {
        return Arrays.asList(values);
    }

    public record ReportFile(byte[] content, String filename) { }
}
