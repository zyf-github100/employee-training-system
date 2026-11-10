package com.zhixing.training.exam;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ExamController {
    private final ExamRepository exams;
    private final QuestionImportService imports;
    private final ExamIntegrityService integrity;
    public ExamController(ExamRepository exams, QuestionImportService imports, ExamIntegrityService integrity) { this.exams = exams; this.imports = imports; this.integrity = integrity; }

    @GetMapping("/admin/questions")
    public ApiResponse<List<ExamDtos.QuestionView>> questions(@RequestParam(defaultValue = "") String keyword, @RequestParam(required = false) String status) { return ApiResponse.ok(exams.questions(keyword, status)); }
    @PostMapping("/admin/questions")
    public ApiResponse<ExamDtos.QuestionView> createQuestion(@Valid @RequestBody ExamDtos.QuestionRequest body, @AuthenticationPrincipal Jwt jwt) { long id=exams.createQuestion(body,user(jwt)); return ApiResponse.ok(exams.question(id).orElseThrow()); }
    @PutMapping("/admin/questions/{id}")
    public ApiResponse<ExamDtos.QuestionView> updateQuestion(@PathVariable long id,@Valid @RequestBody ExamDtos.QuestionRequest body) { exams.updateQuestion(id,body); return ApiResponse.ok(exams.question(id).orElseThrow()); }
    @PostMapping(value="/admin/questions/import", consumes="multipart/form-data")
    public ApiResponse<ExamDtos.ImportResult> importQuestions(@RequestPart("file") MultipartFile file, @RequestHeader(value="X-Request-Id", required=false) String requestId, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(imports.importFile(user(jwt), file, requestId)); }

    @GetMapping("/admin/exams")
    public ApiResponse<List<ExamDtos.ExamView>> adminExams(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.exams(true,user(jwt))); }
    @PostMapping("/admin/exams")
    public ApiResponse<ExamDtos.ExamDetail> createExam(@Valid @RequestBody ExamDtos.ExamRequest body,@AuthenticationPrincipal Jwt jwt) { long id=exams.createExam(body,user(jwt)); return ApiResponse.ok(exams.detail(id,true,user(jwt)).orElseThrow()); }
    @PostMapping("/admin/exams/random")
    public ApiResponse<ExamDtos.ExamDetail> createRandomExam(@Valid @RequestBody ExamDtos.RandomExamRequest body,@AuthenticationPrincipal Jwt jwt) { long id=exams.createRandomExam(body,user(jwt)); return ApiResponse.ok(exams.detail(id,true,user(jwt)).orElseThrow()); }
    @GetMapping("/admin/exams/{id}")
    public ApiResponse<ExamDtos.ExamDetail> adminExam(@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.detail(id,true,user(jwt)).orElseThrow(() -> notFound("EXAM_NOT_FOUND","试卷不存在"))); }
    @PostMapping("/admin/exams/{id}/publish")
    public ApiResponse<ExamDtos.ExamDetail> publish(@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.publish(id,user(jwt))); }

    @GetMapping("/employee/exams")
    public ApiResponse<List<ExamDtos.ExamView>> employeeExams(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.exams(false,user(jwt))); }
    @GetMapping("/employee/exams/{id}")
    public ApiResponse<ExamDtos.ExamDetail> employeeExam(@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.detail(id,false,user(jwt)).orElseThrow(() -> notFound("EXAM_NOT_FOUND","考试不存在或未分配"))); }
    @PostMapping("/employee/exams/{id}/attempts")
    public ApiResponse<ExamDtos.AttemptDetail> begin(@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.begin(id,user(jwt))); }
    @GetMapping("/employee/exam-attempts/active")
    public ApiResponse<List<ExamDtos.AttemptView>> activeAttempts(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.activeAttempts(user(jwt))); }
    @GetMapping("/employee/exam-attempts")
    public ApiResponse<List<ExamDtos.AttemptView>> myAttempts(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.myAttempts(user(jwt))); }
    @GetMapping("/employee/exam-attempts/{id}")
    public ApiResponse<ExamDtos.AttemptDetail> attempt(@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.attemptDetail(id,user(jwt)).orElseThrow(() -> notFound("ATTEMPT_NOT_FOUND","答卷不存在"))); }
    @PutMapping("/employee/exam-attempts/{id}/answers")
    public ApiResponse<ExamDtos.AttemptDetail> saveAnswers(@PathVariable long id, @Valid @RequestBody ExamDtos.SaveAnswersRequest body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(exams.saveAnswers(id, user(jwt), body));
    }
    @PostMapping("/employee/exam-attempts/{id}/submit")
    public ApiResponse<ExamDtos.AttemptView> submit(@PathVariable long id,@Valid @RequestBody ExamDtos.SubmitRequest body,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.submit(id,user(jwt),body)); }

    @GetMapping("/admin/exam-attempts/{id}/grading")
    public ApiResponse<ExamDtos.GradingView> grading(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.grading(id, user(jwt))); }
    @PutMapping("/admin/exam-attempts/{id}/grading")
    public ApiResponse<ExamDtos.GradingView> grade(@PathVariable long id, @Valid @RequestBody ExamDtos.GradeRequest body, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.grade(id, user(jwt), body)); }
    @GetMapping("/admin/exam-attempts")
    public ApiResponse<List<ExamDtos.AttemptView>> submittedAttempts(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(exams.submittedAttempts(user(jwt))); }
    @PostMapping("/employee/exam-attempts/{id}/integrity-events")
    public ApiResponse<ExamDtos.IntegrityView> integrity(@PathVariable long id, @Valid @RequestBody ExamDtos.IntegrityEventRequest body, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(integrity.record(user(jwt), id, body)); }
    @GetMapping("/admin/exam-attempts/{id}/integrity")
    public ApiResponse<ExamDtos.IntegrityView> integrityView(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(integrity.view(id, user(jwt))); }
    @PutMapping("/admin/exam-attempts/{id}/integrity")
    public ApiResponse<ExamDtos.IntegrityView> integrityDisposition(@PathVariable long id, @Valid @RequestBody ExamDtos.IntegrityDispositionRequest body, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(integrity.disposition(user(jwt), id, body)); }

    private static long user(Jwt jwt){return Long.parseLong(jwt.getSubject());}
    private static ApiException notFound(String code,String message){return new ApiException(HttpStatus.NOT_FOUND,code,message);}
}
