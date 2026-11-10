package com.zhixing.training.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class CourseDtos {
    private CourseDtos() { }
    public record CourseView(long id, String title, String summary, String coverUrl, String category, String difficulty, String status,
                             int durationMinutes, int chapterCount, Instant publishedAt, long version) { }
    public record CourseRequest(@NotBlank @Size(max=160) String title, @Size(max=500) String summary, @Size(max=500) String coverUrl,
                                @Size(max=80) String category, @Size(max=20) String difficulty, @Size(max=20) String status,
                                int durationMinutes, long version) { }

    public record ReviewRequest(@jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) int rating,
                                @Size(max=1000) String content, long version) { }
    public record ReviewView(long id, long courseId, long userId, String employeeNo, String authorName,
                             int rating, String content, Instant createdAt, Instant updatedAt, long version) { }
    public record ReviewSummary(double averageRating, long reviewCount, ReviewView mine, List<ReviewView> reviews) { }
    public record DiscussionRequest(@jakarta.validation.constraints.NotBlank @Size(max=2000) String content,
                                    Long parentId) { }
    public record DiscussionStatusRequest(@jakarta.validation.constraints.Pattern(regexp="VISIBLE|HIDDEN") String status,
                                          long version) { }
    public record DiscussionView(long id, long courseId, long userId, String employeeNo, String authorName,
                                 Long parentId, String content, String status, Instant createdAt, Instant updatedAt,
                                 long version) { }
}
