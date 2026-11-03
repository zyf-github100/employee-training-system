package com.zhixing.training.course;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class LearningDtos {
    private LearningDtos() { }
    public record ResourceView(long id, String title, String resourceType, String resourceUrl, int durationSeconds, long sizeBytes, int sortNo,
                               String originalName, String mediaType, String status) { }
    public record ChapterView(long id, long courseId, String title, int sortNo, String contentMarkdown, int durationMinutes, List<ResourceView> resources) { }
    public record CourseDetail(CourseDtos.CourseView course, List<ChapterView> chapters, int progressPercent, int completedChapters, boolean enrolled) { }
    public record ChapterRequest(@NotBlank @Size(max=160) String title, @Min(1) int sortNo, @Size(max=100000) String contentMarkdown, @Min(0) @Max(1440) int durationMinutes) { }
    public record ProgressRequest(@Min(0) @Max(100) int progressPercent, @Min(0) int lastPositionSeconds, boolean completed) { }
    public record ProgressView(long courseId, long chapterId, int progressPercent, int lastPositionSeconds, boolean completed, Instant updatedAt) { }
}
