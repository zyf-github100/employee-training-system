package com.zhixing.training.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class CourseDtos {
    private CourseDtos() { }
    public record CourseView(long id, String title, String summary, String coverUrl, String category, String difficulty, String status,
                             int durationMinutes, int chapterCount, Instant publishedAt, long version) { }
    public record CourseRequest(@NotBlank @Size(max=160) String title, @Size(max=500) String summary, @Size(max=500) String coverUrl,
                                @Size(max=80) String category, @Size(max=20) String difficulty, @Size(max=20) String status,
                                int durationMinutes, long version) { }
}
