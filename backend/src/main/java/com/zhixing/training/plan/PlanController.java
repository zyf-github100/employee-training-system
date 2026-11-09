package com.zhixing.training.plan;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PlanController {
    private final PlanRepository plans;
    public PlanController(PlanRepository plans){this.plans=plans;}
    @GetMapping("/admin/plans") public ApiResponse<List<PlanDtos.PlanView>> admin(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(plans.list(true,user(jwt)));}
    @PostMapping("/admin/plans") public ApiResponse<PlanDtos.PlanDetail> create(@Valid @RequestBody PlanDtos.PlanRequest body,@AuthenticationPrincipal Jwt jwt){long id=plans.create(body,user(jwt));return ApiResponse.ok(plans.detail(id,true,user(jwt)).orElseThrow());}
    @PutMapping("/admin/plans/{id}") public ApiResponse<PlanDtos.PlanDetail> update(@PathVariable long id,@Valid @RequestBody PlanDtos.PlanUpdateRequest body,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(plans.update(id,body,user(jwt)));}
    @PostMapping("/admin/plans/{id}/terminate") public ApiResponse<PlanDtos.PlanDetail> terminate(@PathVariable long id,@Valid @RequestBody PlanDtos.TerminateRequest body,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(plans.terminate(id,body,user(jwt)));}
    @PostMapping("/admin/plans/{id}/extend") public ApiResponse<PlanDtos.PlanDetail> extend(@PathVariable long id,@Valid @RequestBody PlanDtos.ExtendRequest body,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(plans.extend(id,body,user(jwt)));}
    @GetMapping("/admin/plans/{id}") public ApiResponse<PlanDtos.PlanDetail> adminDetail(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(plans.detail(id,true,user(jwt)).orElseThrow(()->notFound()));}
    @GetMapping("/admin/plans/eligible-employees") public ApiResponse<List<PlanDtos.EmployeeOption>> employees(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(plans.eligibleEmployees(user(jwt)));}
    @PostMapping("/admin/plans/{id}/publish") public ApiResponse<PlanDtos.PlanDetail> publish(@PathVariable long id,@Valid @RequestBody PlanDtos.PublishRequest body,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(plans.publish(id,user(jwt),body.employeeIds()));}
    @GetMapping("/employee/plans") public ApiResponse<List<PlanDtos.PlanView>> employee(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(plans.list(false,user(jwt)));}
    @GetMapping("/employee/plans/{id}") public ApiResponse<PlanDtos.PlanDetail> employeeDetail(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(plans.detail(id,false,user(jwt)).orElseThrow(()->notFound()));}
    @PostMapping("/employee/plans/{id}/progress") public ApiResponse<PlanDtos.PlanDetail> progress(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(plans.refreshProgress(id,user(jwt)));}
    private static long user(Jwt jwt){return Long.parseLong(jwt.getSubject());}
    private static ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"PLAN_NOT_FOUND","培训计划不存在或未分配");}
}
