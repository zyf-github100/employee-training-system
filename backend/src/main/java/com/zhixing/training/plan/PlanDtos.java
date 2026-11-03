package com.zhixing.training.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.List;

public final class PlanDtos {
    private PlanDtos() { }
    public record CourseItemRequest(long courseId, boolean required) { }
    public record ExamItemRequest(long examId, boolean required) { }
    public record PlanRequest(@NotBlank @Size(max=160) String title, @Size(max=500) String description,
                              @Size(max=80) String category, @FutureOrPresent LocalDate startDate,
                              @FutureOrPresent LocalDate endDate,
                              @Size(max=30) List<@Valid CourseItemRequest> courses,
                              @Size(max=30) List<@Valid ExamItemRequest> exams) { }
    public record PlanView(long id, String title, String description, String category, LocalDate startDate,
                           LocalDate endDate, String status, int totalItems, int completedItems,
                           int progressPercent, int assigneeCount, long version) { }
    public record PlanItemView(String itemType, long itemId, String title, boolean required,
                               int progressPercent, String status) { }
    public record PlanDetail(PlanView plan, List<PlanItemView> items) { }
    public record PublishRequest(@NotEmpty @Size(max=5000) List<@NotNull @Min(1) Long> employeeIds) { }
    public record EmployeeOption(long id, String employeeNo, String name, String departmentName) { }
}
