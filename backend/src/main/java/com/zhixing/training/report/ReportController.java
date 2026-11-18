package com.zhixing.training.report;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping("/employees")
    public ResponseEntity<byte[]> employees(@Valid @RequestBody(required = false) ReportDtos.ReportQuery query,
                                            @AuthenticationPrincipal Jwt jwt) {
        return download(reports.employees(user(jwt), query));
    }

    @PostMapping("/learning")
    public ResponseEntity<byte[]> learning(@Valid @RequestBody(required = false) ReportDtos.ReportQuery query,
                                           @AuthenticationPrincipal Jwt jwt) {
        return download(reports.learning(user(jwt), query));
    }

    @PostMapping("/exams")
    public ResponseEntity<byte[]> exams(@Valid @RequestBody(required = false) ReportDtos.ReportQuery query,
                                        @AuthenticationPrincipal Jwt jwt) {
        return download(reports.exams(user(jwt), query));
    }

    @PostMapping("/plans")
    public ResponseEntity<byte[]> plans(@Valid @RequestBody(required = false) ReportDtos.ReportQuery query,
                                        @AuthenticationPrincipal Jwt jwt) {
        return download(reports.plans(user(jwt), query));
    }

    private static ResponseEntity<byte[]> download(ReportService.ReportFile file) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .contentLength(file.content().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    private static long user(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
