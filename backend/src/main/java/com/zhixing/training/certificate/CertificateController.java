package com.zhixing.training.certificate;

import com.zhixing.training.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CertificateController {
    private final CertificateRepository certificates;
    private final CertificatePdfService pdf;
    public CertificateController(CertificateRepository certificates,CertificatePdfService pdf){this.certificates=certificates;this.pdf=pdf;}
    @GetMapping("/employee/certificates") public ApiResponse<List<CertificateDtos.CertificateView>> employee(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(certificates.employee(user(jwt)));}
    @GetMapping("/admin/certificates") public ApiResponse<List<CertificateDtos.CertificateView>> admin(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(certificates.admin(user(jwt)));}
    @GetMapping("/employee/certificates/page") public ApiResponse<com.zhixing.training.api.PageResult<CertificateDtos.CertificateView>> employeePage(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status){return ApiResponse.ok(certificates.page(user(jwt),false,pageNo,pageSize,keyword,status));}
    @GetMapping("/admin/certificates/page") public ApiResponse<com.zhixing.training.api.PageResult<CertificateDtos.CertificateView>> adminPage(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status){return ApiResponse.ok(certificates.page(user(jwt),true,pageNo,pageSize,keyword,status));}
    @GetMapping("/employee/certificates/{id}/pdf") public ResponseEntity<byte[]> employeePdf(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return download(certificates.authorized(user(jwt),false,id));}
    @GetMapping("/admin/certificates/{id}/pdf") public ResponseEntity<byte[]> adminPdf(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return download(certificates.authorized(user(jwt),true,id));}
    @PostMapping("/admin/certificates/{id}/revoke") public ApiResponse<CertificateDtos.CertificateView> revoke(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody CertificateDtos.RevokeRequest body){return ApiResponse.ok(certificates.revoke(user(jwt),id,body));}
    @GetMapping("/certificates/verify") public ResponseEntity<ApiResponse<CertificateDtos.VerificationView>> verify(@RequestParam(defaultValue="") String code){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(certificates.verify(code)));}
    private ResponseEntity<byte[]> download(CertificateDtos.CertificateView certificate){return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("certificate-"+certificate.certificateNo()+".pdf").build().toString()).body(pdf.render(certificate));}
    private static long user(Jwt jwt){return Long.parseLong(jwt.getSubject());}
}
