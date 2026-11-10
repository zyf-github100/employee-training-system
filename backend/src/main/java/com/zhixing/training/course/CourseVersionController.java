package com.zhixing.training.course;
import com.zhixing.training.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1/admin/courses/{id}")
public class CourseVersionController {
    private final CourseVersionService service;
    public CourseVersionController(CourseVersionService service){this.service=service;}
    @GetMapping("/revisions") public ApiResponse<List<CourseVersionService.Revision>> history(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.history(id,Long.parseLong(jwt.getSubject())));}
    @PostMapping("/revisions") public ApiResponse<CourseVersionService.Revision> publish(@PathVariable long id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CourseVersionService.PublishRequest body){return ApiResponse.ok(service.publish(id,Long.parseLong(jwt.getSubject()),body));}
    @PostMapping("/revisions/{revision}/restore") public ApiResponse<Void> restore(@PathVariable long id,@PathVariable long revision,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CourseVersionService.RestoreRequest body){service.restore(id,revision,Long.parseLong(jwt.getSubject()),body.version());return ApiResponse.ok();}
}
