package com.zhixing.training.auth;

import com.zhixing.training.api.ApiResponse;
import com.zhixing.training.organization.OrganizationDtos;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class AccountSetupController {
    private final AccountSetupService setup;
    public AccountSetupController(AccountSetupService setup){this.setup=setup;}
    @PostMapping("/admin/employees/{id}/setup-link")
    public ResponseEntity<ApiResponse<AccountSetupService.Issued>> issue(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody OrganizationDtos.VersionRequest b){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(setup.issue(Long.parseLong(jwt.getSubject()),id,b.version())));
    }
    @PostMapping("/auth/account-setup") public ApiResponse<Void> complete(@Valid @RequestBody AccountSetupService.CompleteRequest b,jakarta.servlet.http.HttpServletRequest request){long id=setup.complete(b);request.setAttribute("audit.actorId",id);return ApiResponse.ok();}
}
