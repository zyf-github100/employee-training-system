package com.zhixing.training.course;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.api.ApiResponse;
import com.zhixing.training.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CourseController {
    private final CourseRepository courses;
    private final CourseVersionService versions;
    public CourseController(CourseRepository courses,CourseVersionService versions){this.courses=courses;this.versions=versions;}
    @GetMapping("/employee/courses")
    public ApiResponse<PageResult<CourseDtos.CourseView>> employee(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword){int[] p=page(pageNo,pageSize);return ApiResponse.ok(new PageResult<>(courses.published(p[0],p[1],keyword),p[0],p[1],courses.publishedCount(keyword)));}
    @GetMapping("/admin/courses")
    public ApiResponse<PageResult<CourseDtos.CourseView>> admin(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword){int[] p=page(pageNo,pageSize);return ApiResponse.ok(new PageResult<>(courses.all(p[0],p[1],keyword),p[0],p[1],courses.countAll(keyword)));}
    @PostMapping("/admin/courses") public ApiResponse<CourseDtos.CourseView> create(@Valid @RequestBody CourseDtos.CourseRequest b,@AuthenticationPrincipal Jwt jwt){long id=courses.insert(b,Long.parseLong(jwt.getSubject()));return ApiResponse.ok(courses.find(id));}
    @PutMapping("/admin/courses/{id}") public ApiResponse<CourseDtos.CourseView> update(@PathVariable long id,@Valid @RequestBody CourseDtos.CourseRequest b){
        // Editing a published course changes the draft workspace. Employees continue to read the last released snapshot.
        var current=courses.find(id);
        var body = "PUBLISHED".equals(b.status()) && !"PUBLISHED".equals(current.status()) ? new CourseDtos.CourseRequest(b.title(),b.summary(),b.coverUrl(),b.category(),b.difficulty(),"DRAFT",b.durationMinutes(),b.version()) : b;
        if(!courses.update(id,body))throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","课程已被其他操作更新，请刷新后重试");return ApiResponse.ok(courses.find(id));}
    @PostMapping("/admin/courses/{id}/publish") public ApiResponse<CourseVersionService.Revision> publish(@PathVariable long id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CourseVersionService.PublishRequest body){return ApiResponse.ok(versions.publish(id,Long.parseLong(jwt.getSubject()),body));}
    private static int[] page(int no,int size){if(no<1||size<1||size>200)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","分页参数不合法");return new int[]{no,size};}
}
