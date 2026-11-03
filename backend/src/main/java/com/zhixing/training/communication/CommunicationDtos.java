package com.zhixing.training.communication;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CommunicationDtos {
    private CommunicationDtos() { }
    public record FeedbackRequest(@NotNull UUID requestId, @NotBlank @Size(max=80) String title,
            @NotBlank @Pattern(regexp="课程建议|课程提问|学习反馈|系统问题") String type,
            @NotBlank @Size(max=2000) String content) { }
    public record ReplyRequest(@NotBlank @Size(max=2000) String reply, @Min(0) long version) { }
    public record FeedbackView(long id, String title, String type, String content, String status,
            long userId, String employeeNo, String authorName, String departmentName, String reply,
            String repliedByName, Instant createdAt, Instant repliedAt, long version) { }
    public record AnnouncementRequest(@NotNull UUID requestId, @NotBlank @Size(max=80) String title,
            @NotBlank @Pattern(regexp="培训通知|课程上新|平台公告") String category,
            @NotBlank @Size(max=3000) String content,
            @NotEmpty @Size(max=5000) List<@NotNull @Min(1) Long> employeeIds) { }
    public record VersionRequest(@Min(0) long version) { }
    public record AnnouncementView(long id, String title, String category, String content, String status,
            String publisherName, Instant publishedAt, Instant withdrawnAt, Instant readAt,
            int recipientCount, int readCount, long version) { }
    public record PlatformView(String companyName, String contactName, String contactEmail,
            boolean unreadReminders, long version, Instant updatedAt) { }
    public record PlatformRequest(@NotBlank @Size(max=80) String companyName,
            @NotBlank @Size(max=40) String contactName, @NotNull @Email @Size(max=100) String contactEmail,
            boolean unreadReminders, @Min(0) long version) { }
}
