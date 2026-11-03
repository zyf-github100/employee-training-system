package com.zhixing.training.archive;

import java.time.Instant;
import java.util.List;

public final class ArchiveDtos {
    private ArchiveDtos() { }
    public record EmployeeArchive(String employeeNo, String name, String departmentName,
                                  CourseSummary courses, ExamSummary exams, PlanSummary plans,
                                  List<RecordView> recentRecords) { }
    public record CourseSummary(int enrolled, int completed, int averageProgressPercent, int completedMinutes) { }
    public record ExamSummary(int attempts, int submitted, int passed, int averageScore) { }
    public record PlanSummary(int assigned, int completed, int averageProgressPercent) { }
    public record RecordView(String recordType, long recordId, String title, String status,
                             int progressPercent, Integer score, Instant updatedAt) { }
    public record AnalyticsSummary(int activeEmployees, int publishedCourses, int enrollments,
                                   int completedEnrollments, int courseCompletionRate,
                                   int publishedExams, int submittedAttempts, int passedAttempts,
                                   int examPassRate, int publishedPlans, int planAssignments,
                                   int completedPlanAssignments, int planCompletionRate) { }
}
