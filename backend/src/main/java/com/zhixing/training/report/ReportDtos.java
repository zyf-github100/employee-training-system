package com.zhixing.training.report;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class ReportDtos {
    private ReportDtos() { }

    public record ReportQuery(@Size(max = 80) String keyword,
                              @Min(1) Long departmentId,
                              LocalDate from,
                              LocalDate to) { }

    public record EmployeeRow(String employeeNo, String name, String department, String status,
                              boolean activated, int enrolled, int completed, int completedMinutes,
                              int attempts, int submitted, int passed, int averageScore,
                              int plans, int plansCompleted, int certificates, Instant lastActive) { }

    public record LearningRow(String employeeNo, String name, String department, String course,
                              String chapter, int progressPercent, int watchedSeconds,
                              Instant completedAt, Instant lastActivity) { }

    public record ExamRow(String employeeNo, String name, String department, String exam, int attemptNo,
                          String status, Integer score, Boolean passed, Instant startedAt,
                          Instant submittedAt, String integrityStatus, String gradingStatus) { }

    public record PlanRow(String employeeNo, String name, String department, String plan,
                          String planStatus, String assignmentStatus, int progressPercent,
                          Instant startedAt, Instant completedAt, LocalDate planStart,
                          LocalDate planEnd, String terminationReason) { }
}
