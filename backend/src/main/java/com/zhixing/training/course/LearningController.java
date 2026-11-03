package com.zhixing.training.course;

import com.zhixing.training.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class LearningController {
    private final LearningRepository learning;
    public LearningController(LearningRepository learning) { this.learning=learning; }

    @GetMapping("/employee/courses/{courseId}")
    public ApiResponse<LearningDtos.CourseDetail> employeeDetail(@PathVariable long courseId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(learning.detail(courseId, Long.parseLong(jwt.getSubject()), true));
    }
    @PostMapping("/employee/courses/{courseId}/enrollment")
    public ApiResponse<LearningDtos.CourseDetail> enroll(@PathVariable long courseId, @AuthenticationPrincipal Jwt jwt) {
        long userId=Long.parseLong(jwt.getSubject()); learning.enrollment(courseId,userId); return ApiResponse.ok(learning.detail(courseId,userId,true));
    }
    @PutMapping("/employee/courses/{courseId}/chapters/{chapterId}/progress")
    public ApiResponse<LearningDtos.ProgressView> progress(@PathVariable long courseId,@PathVariable long chapterId,@Valid @RequestBody LearningDtos.ProgressRequest body,@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(learning.updateProgress(courseId,chapterId,Long.parseLong(jwt.getSubject()),body));
    }
    @GetMapping("/employee/courses/{courseId}/progress")
    public ApiResponse<List<LearningDtos.ProgressView>> progress(@PathVariable long courseId,@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(learning.progress(courseId,Long.parseLong(jwt.getSubject())));
    }
    @GetMapping("/admin/courses/{courseId}")
    public ApiResponse<LearningDtos.CourseDetail> adminDetail(@PathVariable long courseId,@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(learning.detail(courseId,Long.parseLong(jwt.getSubject()),false));
    }
    @PostMapping("/admin/courses/{courseId}/chapters")
    public ApiResponse<LearningDtos.CourseDetail> createChapter(@PathVariable long courseId,@Valid @RequestBody LearningDtos.ChapterRequest body,@AuthenticationPrincipal Jwt jwt) {
        learning.insertChapter(courseId,body); return ApiResponse.ok(learning.detail(courseId,Long.parseLong(jwt.getSubject()),false));
    }
    @PutMapping("/admin/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<LearningDtos.CourseDetail> updateChapter(@PathVariable long courseId,@PathVariable long chapterId,@Valid @RequestBody LearningDtos.ChapterRequest body,@AuthenticationPrincipal Jwt jwt) {
        if(!learning.updateChapter(courseId,chapterId,body)) throw new com.zhixing.training.api.ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"CHAPTER_NOT_FOUND","章节不存在");
        return ApiResponse.ok(learning.detail(courseId,Long.parseLong(jwt.getSubject()),false));
    }
}
