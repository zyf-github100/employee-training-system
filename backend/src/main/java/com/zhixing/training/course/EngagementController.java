package com.zhixing.training.course;

import com.zhixing.training.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.zhixing.training.course.CourseDtos.*;

@RestController
@RequestMapping("/api/v1")
public class EngagementController {
    private final EngagementRepository engagement;
    public EngagementController(EngagementRepository engagement) { this.engagement = engagement; }

    @GetMapping("/employee/courses/{courseId}/reviews")
    public ApiResponse<ReviewSummary> reviews(@PathVariable long courseId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(engagement.reviews(courseId, user(jwt)));
    }

    @PostMapping("/employee/courses/{courseId}/reviews")
    public ApiResponse<ReviewView> review(@PathVariable long courseId, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReviewRequest body) {
        return ApiResponse.ok(engagement.upsertReview(courseId, user(jwt), body));
    }

    @GetMapping("/employee/courses/{courseId}/discussions")
    public ApiResponse<List<DiscussionView>> discussions(@PathVariable long courseId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(engagement.discussions(courseId, user(jwt), false));
    }

    @PostMapping("/employee/courses/{courseId}/discussions")
    public ApiResponse<DiscussionView> discussion(@PathVariable long courseId, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DiscussionRequest body) {
        return ApiResponse.ok(engagement.createDiscussion(courseId, user(jwt), body));
    }

    @GetMapping("/admin/courses/{courseId}/discussions")
    public ApiResponse<List<DiscussionView>> adminDiscussions(@PathVariable long courseId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(engagement.discussions(courseId, user(jwt), true));
    }

    @PutMapping("/admin/course-discussions/{id}/status")
    public ApiResponse<DiscussionView> status(@PathVariable long id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DiscussionStatusRequest body) {
        return ApiResponse.ok(engagement.setStatus(user(jwt), id, body));
    }

    private static long user(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
