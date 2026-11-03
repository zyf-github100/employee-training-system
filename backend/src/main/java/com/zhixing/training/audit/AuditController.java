package com.zhixing.training.audit;

import com.zhixing.training.api.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit){this.audit=audit;}
    @GetMapping public ApiResponse<PageResult<AuditService.Entry>> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String outcome) {
        return ApiResponse.ok(audit.list(Long.parseLong(jwt.getSubject()),pageNo,pageSize,outcome));
    }
}
