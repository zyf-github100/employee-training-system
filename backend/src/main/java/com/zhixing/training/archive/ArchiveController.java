package com.zhixing.training.archive;

import com.zhixing.training.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1")
public class ArchiveController {
    private final ArchiveRepository archive;
    public ArchiveController(ArchiveRepository archive){this.archive=archive;}
    @GetMapping("/employee/archive") public ApiResponse<ArchiveDtos.EmployeeArchive> employee(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(archive.employee(user(jwt)));}
    @GetMapping("/admin/analytics") public ApiResponse<ArchiveDtos.AnalyticsSummary> analytics(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(archive.analytics(user(jwt)));}
    @GetMapping("/admin/employees/{id}/archive") public ApiResponse<ArchiveDtos.EmployeeArchive> adminEmployee(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(archive.adminEmployee(user(jwt),id));}
    private static long user(Jwt jwt){return Long.parseLong(jwt.getSubject());}
}
