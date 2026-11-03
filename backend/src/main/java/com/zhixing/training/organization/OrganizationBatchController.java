package com.zhixing.training.organization;

import com.zhixing.training.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.zhixing.training.organization.OrganizationBatchDtos.*;

@RestController
@RequestMapping("/api/v1/admin/employees/batch")
public class OrganizationBatchController {
    private final OrganizationBatchService service;
    public OrganizationBatchController(OrganizationBatchService service) { this.service=service; }
    @PostMapping("/transfer")
    public ApiResponse<Result> transfer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransferRequest body) {
        return ApiResponse.ok(service.transfer(Long.parseLong(jwt.getSubject()), body));
    }
    @PostMapping("/roles")
    public ApiResponse<Result> roles(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RoleRequest body) {
        return ApiResponse.ok(service.roles(Long.parseLong(jwt.getSubject()), body));
    }
}
