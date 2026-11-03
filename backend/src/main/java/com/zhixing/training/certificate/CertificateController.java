package com.zhixing.training.certificate;

import com.zhixing.training.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CertificateController {
    private final CertificateRepository certificates;
    public CertificateController(CertificateRepository certificates){this.certificates=certificates;}
    @GetMapping("/employee/certificates") public ApiResponse<List<CertificateDtos.CertificateView>> employee(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(certificates.employee(user(jwt)));}
    @GetMapping("/admin/certificates") public ApiResponse<List<CertificateDtos.CertificateView>> admin(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(certificates.admin(user(jwt)));}
    private static long user(Jwt jwt){return Long.parseLong(jwt.getSubject());}
}
