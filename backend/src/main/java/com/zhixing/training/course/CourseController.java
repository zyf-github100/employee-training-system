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
    public CourseController(CourseRepository courses){this.courses=courses;}
    @GetMapping("/employee/courses")
    public ApiResponse<PageResult<CourseDtos.CourseView>> employee(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword){int[] p=page(pageNo,pageSize);return ApiResponse.ok(new PageResult<>(courses.published(p[0],p[1],keyword),p[0],p[1],courses.publishedCount(keyword)));}
    @GetMapping("/admin/courses")
    public ApiResponse<PageResult<CourseDtos.CourseView>> admin(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword){int[] p=page(pageNo,pageSize);return ApiResponse.ok(new PageResult<>(courses.all(p[0],p[1],keyword),p[0],p[1],courses.countAll(keyword)));}
    @PostMapping("/admin/courses") public ApiResponse<CourseDtos.CourseView> create(@Valid @RequestBody CourseDtos.CourseRequest b,@AuthenticationPrincipal Jwt jwt){long id=courses.insert(b,Long.parseLong(jwt.getSubject()));return ApiResponse.ok(courses.find(id));}
    @PutMapping("/admin/courses/{id}") public ApiResponse<CourseDtos.CourseView> update(@PathVariable long id,@Valid @RequestBody CourseDtos.CourseRequest b){if(!courses.update(id,b))throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","课程已被其他操作更新，请刷新后重试");return ApiResponse.ok(courses.find(id));}
    private static int[] page(int no,int size){if(no<1||size<1||size>200)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","分页参数不合法");return new int[]{no,size};}
}
